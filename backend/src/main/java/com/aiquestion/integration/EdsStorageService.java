package com.aiquestion.integration;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.VideoLog;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.EdsProperties;
import com.aiquestion.storage.ObjectStorage;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.UploadPartRequest;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** EDS 的 S3 适配：按内网部署要求，仅此客户端跳过 TLS 证书和主机名校验。 */
@Service
public class EdsStorageService implements ObjectStorage, AutoCloseable {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EdsStorageService.class);
  private final EdsProperties properties;
  public EdsStorageService(EdsProperties properties) { this.properties = properties; }
  private AmazonS3 sharedClient;
  private boolean closed;

  // Lazy initialization keeps an unused, unconfigured provider from blocking application startup.
  private synchronized AmazonS3 client() {
    if (closed) throw new IllegalStateException("Storage client is closed");
    if (sharedClient == null) {
      sharedClient = createClient();
      log.info("video event=storage_client_ready provider=eds bucket={} region={}", properties.bucket(), properties.region());
    }
    return sharedClient;
  }

  @jakarta.annotation.PreDestroy
  @Override public synchronized void close() {
    if (closed) return;
    closed = true;
    if (sharedClient != null) sharedClient.shutdown();
  }

  @Override public String provider() { return "eds"; }

  @Override public void validateConfiguration() {
    if (blank(properties.endpoint()) || blank(properties.bucket()) || blank(properties.accessKeyId())
        || blank(properties.secretKey())) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "EDS 存储配置未完成");
    }
    URI endpoint;
    try { endpoint = URI.create(properties.endpoint()); }
    catch (IllegalArgumentException ex) { throw new IllegalArgumentException("EDS_ENDPOINT 地址格式无效"); }
    if (!("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme()))
        || endpoint.getHost() == null || endpoint.getUserInfo() != null
        || endpoint.getQuery() != null || endpoint.getFragment() != null) {
      throw new IllegalArgumentException("EDS_ENDPOINT 必须是 HTTP 或 HTTPS 服务地址");
    }
  }

  protected AmazonS3 createClient() {
    validateConfiguration();
    URI endpoint = URI.create(properties.endpoint());
    log.info("video event=storage_client_init provider=eds host={} port={} scheme={} bucket={} region={}",
        endpoint.getHost(), endpoint.getPort(), endpoint.getScheme(), properties.bucket(), properties.region());
    ClientConfiguration config = new ClientConfiguration();
    // Initial bounded pool; size must be validated against storage latency and video traffic.
    config.setMaxConnections(128);
    config.setConnectionTTL(300000);
    config.setConnectionMaxIdleMillis(60000);
    config.setConnectionTimeout(10000);
    config.setSocketTimeout(60000);
    config.setMaxErrorRetry(2);
    if ("https".equalsIgnoreCase(URI.create(properties.endpoint()).getScheme())) {
      try {
        var sslContext = org.apache.http.ssl.SSLContexts.custom()
            .loadTrustMaterial(null, (chain, authType) -> true).build();
        config.getApacheHttpClientConfig().setSslSocketFactory(
            new org.apache.http.conn.ssl.SSLConnectionSocketFactory(
                sslContext, org.apache.http.conn.ssl.NoopHostnameVerifier.INSTANCE));
      } catch (java.security.GeneralSecurityException ex) {
        throw new IllegalStateException("EDS HTTPS 客户端初始化失败", ex);
      }
    }
    return AmazonS3ClientBuilder.standard()
        .withPathStyleAccessEnabled(true)
        .withChunkedEncodingDisabled(true)
        .withCredentials(new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(properties.accessKeyId(), properties.secretKey())))
        .withClientConfiguration(config)
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(properties.endpoint(),
            properties.region() == null ? "" : properties.region()))
        .build();
  }

  @Override public void put(MultipartFile file, String mime, String key) {
    long start = System.nanoTime();
    boolean video = VideoObjectLog.applies(key);
    boolean multipart = VideoUploadParts.required(key, file.getSize());
    if (video) log.info("video event=origin_upload_start provider={} bucket={} key={} bytes={} multipart={}", provider(), properties.bucket(), key, file.getSize(), multipart);
    AmazonS3 client = client();
    try (var input = file.getInputStream()) {
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentType(mime);
      if (multipart) {
        putVideoParts(client, key, metadata, input, file.getSize());
      } else {
        metadata.setContentLength(file.getSize());
        client.putObject(new PutObjectRequest(properties.bucket(), key, input, metadata));
      }
      if (video) log.info("video event=origin_upload_done provider={} key={} bytes={} elapsedMs={}", provider(), key, file.getSize(), VideoLog.elapsedMs(start));
    } catch (Exception ex) {
      if (video) VideoObjectLog.failure(provider(), "upload", key, start, ex);
      else logUploadFailure(ex, key, mime, file.getSize());
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "EDS 文件上传失败，请检查存储连接和权限");
    }
  }

  private void putVideoParts(AmazonS3 client, String key, ObjectMetadata metadata,
      java.io.InputStream input, long size) throws java.io.IOException {
    String bucket = properties.bucket();
    long start = System.nanoTime();
    log.info("video event=origin_multipart_start provider={} key={} bytes={}", provider(), key, size);
    String uploadId = client.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket, key, metadata)).getUploadId();
    try {
      var tags = VideoUploadParts.send(input, size, (number, part, length) -> {
        long partStart = System.nanoTime();
        try {
          var tag = client.uploadPart(new UploadPartRequest().withBucketName(bucket).withKey(key)
                .withUploadId(uploadId).withPartNumber(number).withInputStream(part).withPartSize(length)).getPartETag();
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
    AmazonS3 client = client();
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
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "EDS 文件读取失败，请检查存储连接和权限");
    }
  }

  @Override public void delete(String key) {
    long start = System.nanoTime();
    AmazonS3 client = client();
    try {
      client.deleteObject(properties.bucket(), key);
      if (VideoObjectLog.applies(key)) log.info("video event=origin_deleted provider={} key={} elapsedMs={}", provider(), key, VideoLog.elapsedMs(start));
    } catch (Exception ex) {
      if (VideoObjectLog.applies(key)) VideoObjectLog.failure(provider(), "delete", key, start, ex);
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "EDS 文件删除失败");
    }
  }

  private void logUploadFailure(Exception failure, String key, String mime, long size) {
    var trace = new java.io.StringWriter();
    failure.printStackTrace(new java.io.PrintWriter(trace));
    String details = "EDS upload failed: endpoint=" + properties.endpoint()
        + ", bucket=" + properties.bucket() + ", region=" + properties.region()
        + ", objectKey=" + key + ", contentType=" + mime + ", size=" + size;
    if (failure instanceof com.amazonaws.AmazonServiceException serviceError) {
      details += ", status=" + serviceError.getStatusCode()
          + ", errorCode=" + serviceError.getErrorCode()
          + ", requestId=" + serviceError.getRequestId();
    }
    // SDK errors can echo credentials (for example InvalidAccessKeyId). Redact before logging.
    String message = details + System.lineSeparator() + trace;
    if (!blank(properties.accessKeyId())) message = message.replace(properties.accessKeyId(), "[REDACTED]");
    if (!blank(properties.secretKey())) message = message.replace(properties.secretKey(), "[REDACTED]");
    log.error("{}", message);
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
}
