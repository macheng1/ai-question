package com.aiquestion.integration;

import com.aiquestion.common.BusinessException;
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
import com.amazonaws.services.s3.model.PutObjectRequest;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** EDS 的 S3 适配：按内网部署要求，仅此客户端跳过 TLS 证书和主机名校验。 */
@Service
public class EdsStorageService implements ObjectStorage {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EdsStorageService.class);
  private final EdsProperties properties;
  public EdsStorageService(EdsProperties properties) { this.properties = properties; }
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
    ClientConfiguration config = new ClientConfiguration();
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
    AmazonS3 client = createClient();
    try (var input = file.getInputStream()) {
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(file.getSize());
      metadata.setContentType(mime);
      client.putObject(new PutObjectRequest(properties.bucket(), key, input, metadata));
    } catch (Exception ex) {
      logUploadFailure(ex, key, mime, file.getSize());
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "EDS 文件上传失败，请检查存储连接和权限");
    } finally { client.shutdown(); }
  }

  @Override public StoredObject open(String key) {
    AmazonS3 client = createClient();
    try {
      var object = client.getObject(properties.bucket(), key);
      var stream = new java.io.FilterInputStream(object.getObjectContent()) {
        @Override public void close() throws java.io.IOException {
          try { super.close(); } finally { client.shutdown(); }
        }
      };
      return new StoredObject(stream, object.getObjectMetadata().getContentLength(),
          object.getObjectMetadata().getContentType());
    } catch (Exception ex) {
      client.shutdown();
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "EDS 文件读取失败，请检查存储连接和权限");
    }
  }

  @Override public void delete(String key) {
    AmazonS3 client = createClient();
    try { client.deleteObject(properties.bucket(), key); }
    catch (Exception ex) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "EDS 文件删除失败"); }
    finally { client.shutdown(); }
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
