package com.aiquestion.integration;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.VideoLog;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.AliyunOssProperties;
import com.aiquestion.storage.ObjectStorage;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.ListMultipartUploadsRequest;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.UploadPartRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 阿里云 OSS 适配层，负责私有对象操作和连接释放。 */
@Service
public class AliyunOssService implements ObjectStorage, com.aiquestion.storage.VideoMultipartStorage, AutoCloseable {
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
        try {
          client.putObject(new PutObjectRequest(properties.bucket(), key, input, metadata));
        } catch (com.aliyun.oss.ClientException failure) {
          // A PUT can succeed even when its response is lost. Verify this single-part object before proceeding.
          if (!video || !matchesStoredFile(client, key, file, failure)) throw failure;
          log.info("video event=origin_upload_verified provider={} key={} bytes={}", provider(), key, file.getSize());
        }
      }
      if (video) log.info("video event=origin_upload_done provider={} key={} bytes={} elapsedMs={}", provider(), key, file.getSize(), VideoLog.elapsedMs(start));
    } catch (Exception ex) {
      if (video) VideoObjectLog.failure(provider(), "upload", key, start, ex);
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传 OSS 失败");
    }
  }

  private boolean matchesStoredFile(OSS client, String key, MultipartFile file, RuntimeException failure) {
    try {
      var stored = client.getObjectMetadata(properties.bucket(), key);
      if (stored.getContentLength() != file.getSize() || stored.getETag() == null) return false;
      try (var source = file.getInputStream()) {
        return org.springframework.util.DigestUtils.md5DigestAsHex(source).equalsIgnoreCase(stored.getETag());
      }
    } catch (RuntimeException | java.io.IOException verificationError) {
      failure.addSuppressed(verificationError);
      return false;
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


  @Override public String beginVideo(String key, String type) {
    var metadata = new ObjectMetadata();
    metadata.setContentType(type);
    metadata.setHeader("x-oss-object-acl", "private");
    return client().initiateMultipartUpload(new InitiateMultipartUploadRequest(properties.bucket(), key, metadata)).getUploadId();
  }

  @Override public String signVideoPart(String key, String upload, int number) {
    var request = new GeneratePresignedUrlRequest(properties.bucket(), key, com.aliyun.oss.HttpMethod.PUT);
    request.setExpiration(java.util.Date.from(java.time.Instant.now().plusSeconds(600)));
    request.setContentType("application/octet-stream");
    request.addQueryParameter("uploadId", upload);
    request.addQueryParameter("partNumber", Integer.toString(number));
    return client().generatePresignedUrl(request).toExternalForm().replaceFirst("^http://", "https://");
  }

  @Override public void putVideoPart(String key, String upload, int number, InputStream input, long bytes) {
    client().uploadPart(new UploadPartRequest(properties.bucket(), key, upload, number, input, bytes));
  }

  @Override public List<Part> videoParts(String key, String upload) {
    var request = new ListPartsRequest(properties.bucket(), key, upload);
    request.setMaxParts(1000); // Part sizing keeps each session within a single listing.
    var listing = client().listParts(request);
    if (listing.isTruncated()) throw new BusinessException(ErrorCode.BAD_REQUEST, "视频分片数量无效");
    return listing.getParts().stream().map(p -> new Part(p.getPartNumber(), p.getSize(), p.getETag())).toList();
  }

  @Override public void finishVideo(String key, String upload, List<Part> parts) {
    // The OSS SDK sorts this list in place before sending the completion request.
    var tags = new java.util.ArrayList<PartETag>(parts.size());
    for (var part : parts) tags.add(new PartETag(part.number(), part.etag()));
    client().completeMultipartUpload(new CompleteMultipartUploadRequest(properties.bucket(), key, upload, tags));
  }

  @Override public boolean videoExists(String key) {
    return client().doesObjectExist(properties.bucket(), key);
  }

  @Override public long videoLength(String key) {
    return client().getObjectMetadata(properties.bucket(), key).getContentLength();
  }

  @Override public byte[] videoHeader(String key) throws IOException {
    var request = new GetObjectRequest(properties.bucket(), key);
    request.setRange(0, 11);
    try (var object = client().getObject(request)) {
      return object.getObjectContent().readNBytes(12);
    }
  }

  @Override public void abortVideo(String key, String upload) {
    try {
      client().abortMultipartUpload(new AbortMultipartUploadRequest(properties.bucket(), key, upload));
    } catch (com.aliyun.oss.OSSException error) {
      if (!"NoSuchUpload".equals(error.getErrorCode())) throw error;
    }
  }

  @Override public List<String> pendingVideoUploads(String key) {
    var request = new ListMultipartUploadsRequest(properties.bucket());
    request.setPrefix(key);
    return client().listMultipartUploads(request).getMultipartUploads().stream()
        .filter(u -> key.equals(u.getKey())).map(u -> u.getUploadId()).toList();
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
    config.setProtocol(com.aliyun.oss.common.comm.Protocol.HTTPS);
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
