package com.aiquestion.integration;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.VideoLog;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.AliyunOssProperties;
import com.aiquestion.storage.ObjectStorage;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.UploadPartRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 阿里云 OSS 适配层，负责私有对象操作和连接释放。 */
@Service
public class AliyunOssService implements ObjectStorage, AutoCloseable {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AliyunOssService.class);
  private final AliyunOssProperties properties;

  public AliyunOssService(AliyunOssProperties properties) { this.properties = properties; }
  private OSS sharedClient;
  private boolean closed;

  // Lazy initialization keeps an unused, unconfigured provider from blocking application startup.
  private synchronized OSS client() {
    if (closed) throw new IllegalStateException("Storage client is closed");
    if (sharedClient == null) sharedClient = createClient();
    return sharedClient;
  }

  @jakarta.annotation.PreDestroy
  @Override public synchronized void close() {
    if (closed) return;
    closed = true;
    if (sharedClient != null) sharedClient.shutdown();
  }

  @Override public String provider() { return "aliyun"; }

  @Override public void validateConfiguration() {
    if (blank(properties.accessKeyId()) || blank(properties.accessKeySecret())
        || blank(properties.bucket()) || blank(properties.endpoint())) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "阿里云 OSS 配置未完成");
    }
  }

  @Override public void put(MultipartFile file, String contentType, String key) {
    long start = System.nanoTime();
    boolean video = VideoObjectLog.applies(key);
    boolean multipart = VideoUploadParts.required(key, file.getSize());
    if (video) log.info("video event=origin_upload_start provider={} bucket={} key={} bytes={} multipart={}", provider(), properties.bucket(), key, file.getSize(), multipart);
    OSS client = client();
    try (var input = file.getInputStream()) {
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentType(contentType);
      metadata.setHeader("x-oss-object-acl", "private");
      if (multipart) {
        putVideoParts(client, key, metadata, input, file.getSize());
      } else {
        metadata.setContentLength(file.getSize());
        client.putObject(new PutObjectRequest(properties.bucket(), key, input, metadata));
      }
      if (video) log.info("video event=origin_upload_done provider={} key={} bytes={} elapsedMs={}", provider(), key, file.getSize(), VideoLog.elapsedMs(start));
    } catch (Exception ex) {
      if (video) VideoObjectLog.failure(provider(), "upload", key, start, ex);
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传 OSS 失败");
    }
  }

  private void putVideoParts(OSS client, String key, ObjectMetadata metadata,
      java.io.InputStream input, long size) throws java.io.IOException {
    String bucket = properties.bucket();
    long start = System.nanoTime();
    log.info("video event=origin_multipart_start provider={} key={} bytes={}", provider(), key, size);
    String uploadId = client.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket, key, metadata)).getUploadId();
    try {
      var tags = VideoUploadParts.send(input, size, (number, part, length) -> {
        long partStart = System.nanoTime();
        try {
          var tag = client.uploadPart(new UploadPartRequest(bucket, key, uploadId, number, part, length)).getPartETag();
          log.info("video event=origin_part_done provider={} key={} part={} bytes={} elapsedMs={}", provider(), key, number, length, VideoLog.elapsedMs(partStart));
          return tag;
        } catch (RuntimeException ex) {
          log.warn("video event=origin_part_failed provider={} key={} part={} bytes={} elapsedMs={}", provider(), key, number, length, VideoLog.elapsedMs(partStart));
          throw ex;
        }
      });
      log.info("video event=origin_multipart_completing provider={} key={} parts={} elapsedMs={}", provider(), key, tags.size(), VideoLog.elapsedMs(start));
      client.completeMultipartUpload(new CompleteMultipartUploadRequest(bucket, key, uploadId, tags));
    } catch (java.io.IOException | RuntimeException failure) {
      try {
        client.abortMultipartUpload(new AbortMultipartUploadRequest(bucket, key, uploadId));
        log.warn("video event=origin_multipart_aborted provider={} key={} elapsedMs={}", provider(), key, VideoLog.elapsedMs(start));
      } catch (RuntimeException cleanup) {
        VideoObjectLog.failure(provider(), "abort", key, start, cleanup);
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  @Override public StoredObject open(String key) {
    long start = System.nanoTime();
    OSS client = client();
    try {
      var object = client.getObject(properties.bucket(), key);
      var stream = object.getObjectContent();
      try {
        long length = object.getObjectMetadata().getContentLength();
        return new StoredObject(VideoObjectLog.observe(provider(), key, length, start, stream), length,
            object.getObjectMetadata().getContentType());
      } catch (RuntimeException failure) {
        // Ownership transfers to the caller only after a StoredObject was successfully built.
        try { object.close(); } catch (java.io.IOException cleanup) { failure.addSuppressed(cleanup); }
        throw failure;
      }
    } catch (Exception ex) {
      if (VideoObjectLog.applies(key)) VideoObjectLog.failure(provider(), "open", key, start, ex);
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件读取失败，请稍后重试");
    }
  }

  @Override public java.util.Optional<String> directPreviewUrl(String key) {
    String endpoint = properties.endpoint();
    if (blank(endpoint) || endpoint.contains("-internal.") || endpoint.contains(".internal.")) {
      return java.util.Optional.empty();
    }
    var expires = java.util.Date.from(java.time.Instant.now().plusSeconds(120));
    var signed = client().generatePresignedUrl(properties.bucket(), key, expires);
    return java.util.Optional.of(signed.toExternalForm().replaceFirst("^http://", "https://"));
  }

  @Override public void delete(String key) {
    long start = System.nanoTime();
    OSS client = client();
    try {
      client.deleteObject(properties.bucket(), key);
      if (VideoObjectLog.applies(key)) log.info("video event=origin_deleted provider={} key={} elapsedMs={}", provider(), key, VideoLog.elapsedMs(start));
    } catch (Exception ex) {
      if (VideoObjectLog.applies(key)) VideoObjectLog.failure(provider(), "delete", key, start, ex);
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败");
    }
  }

  protected OSS createClient() {
    validateConfiguration();
    var config = new com.aliyun.oss.ClientBuilderConfiguration();
    config.setMaxConnections(128);
    config.setConnectionTimeout(10000);
    config.setConnectionRequestTimeout(10000);
    config.setSocketTimeout(60000);
    config.setConnectionTTL(300000);
    config.setIdleConnectionTime(60000);
    config.setMaxErrorRetry(2);
    return new OSSClientBuilder().build(properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret(), config);
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
}
