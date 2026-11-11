package com.aiquestion.integration;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.AliyunOssProperties;
import com.aiquestion.storage.ObjectStorage;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 阿里云 OSS 适配层，负责私有对象操作和连接释放。 */
@Service
public class AliyunOssService implements ObjectStorage {
  private final AliyunOssProperties properties;

  public AliyunOssService(AliyunOssProperties properties) { this.properties = properties; }
  @Override public String provider() { return "aliyun"; }

  @Override public void validateConfiguration() {
    if (blank(properties.accessKeyId()) || blank(properties.accessKeySecret())
        || blank(properties.bucket()) || blank(properties.endpoint())) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "阿里云 OSS 配置未完成");
    }
  }

  @Override public void put(MultipartFile file, String contentType, String key) {
    OSS client = createClient();
    try (var input = file.getInputStream()) {
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(file.getSize());
      metadata.setContentType(contentType);
      metadata.setHeader("x-oss-object-acl", "private");
      client.putObject(new PutObjectRequest(properties.bucket(), key, input, metadata));
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传 OSS 失败");
    } finally { client.shutdown(); }
  }

  @Override public StoredObject open(String key) {
    OSS client = createClient();
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
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件读取失败，请稍后重试");
    }
  }

  @Override public void delete(String key) {
    OSS client = createClient();
    try { client.deleteObject(properties.bucket(), key); }
    catch (Exception ex) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败"); }
    finally { client.shutdown(); }
  }

  protected OSS createClient() {
    validateConfiguration();
    return new OSSClientBuilder().build(properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret());
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
}
