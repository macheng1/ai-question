package com.aiquestion.integration;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.AliyunOssProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 阿里云 OSS 适配层，统一文件命名和资源释放。 */
@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class AliyunOssService {
  private final AliyunOssProperties properties;

  public AliyunOssService(AliyunOssProperties properties) {
    this.properties = properties;
  }

  public OssUploadResult upload(MultipartFile file, String directory) {
    if (properties.accessKeyId() == null
        || properties.accessKeyId().isBlank()
        || properties.bucket() == null
        || properties.bucket().isBlank())
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "阿里云 OSS 配置未完成");
    String key =
        (directory == null ? "uploads" : directory).replaceAll("^/+|/+$", "")
            + "/"
            + UUID.randomUUID()
            + "-"
            + file.getOriginalFilename();
    OSS client =
        new OSSClientBuilder()
            .build(properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret());
    try (var input = file.getInputStream()) {
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentLength(file.getSize());
      metadata.setContentType(file.getContentType());
      client.putObject(new PutObjectRequest(properties.bucket(), key, input, metadata));
      return new OssUploadResult(
          key,
          file.getOriginalFilename(),
          "https://" + properties.bucket() + "." + properties.endpoint() + "/" + key,
          file.getSize(),
          file.getContentType());
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
    } finally {
      client.shutdown();
    }
  }
}
