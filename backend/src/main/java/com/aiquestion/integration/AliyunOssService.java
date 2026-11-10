package com.aiquestion.integration;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.AliyunOssProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 阿里云 OSS 适配层，统一文件命名和资源释放。
 */
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
                || properties.bucket().isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "阿里云 OSS 配置未完成");
        }
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

    /** 培训视频使用短对象键和私有 ACL，不受原始文件名长度、字符或桶默认 ACL 影响。 */
    public String uploadTrainingVideo(MultipartFile file, String contentType) {
        return uploadTrainingMaterial(file, contentType, "video/mp4".equals(contentType) ? "mp4" : "webm", "training-videos");
    }

    public String uploadTrainingMaterial(MultipartFile file, String contentType, String suffix, String folder) {
        if (!java.util.Set.of("mp4", "webm", "pdf", "png", "jpg").contains(suffix) || !java.util.Set.of("training-videos", "training-materials").contains(folder)) throw new BusinessException(ErrorCode.BAD_REQUEST);
        String key = folder + "/" + UUID.randomUUID() + "." + suffix;
        OSS client = createClient();
        try (var input = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType);
            metadata.setHeader("x-oss-object-acl", "private");
            client.putObject(new PutObjectRequest(properties.bucket(), key, input, metadata));
            return key;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "培训视频上传 OSS 失败");
        } finally { client.shutdown(); }
    }

    /** 流式代理私有对象，客户端连接关闭时同时释放 OSS 连接。 */
    public StoredObject openTrainingVideo(String key) {
        OSS client = createClient();
        try {
            var object = client.getObject(properties.bucket(), key);
            var stream = new java.io.FilterInputStream(object.getObjectContent()) {
                @Override public void close() throws java.io.IOException {
                    try { super.close(); } finally { client.shutdown(); }
                }
            };
            return new StoredObject(stream, object.getObjectMetadata().getContentLength());
        } catch (Exception ex) {
            client.shutdown();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "培训视频读取失败，请稍后重试");
        }
    }

    public void deleteTrainingVideo(String key) {
        OSS client = createClient();
        try { client.deleteObject(properties.bucket(), key); }
        catch (Exception ex) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "培训视频删除失败"); }
        finally { client.shutdown(); }
    }

    protected OSS createClient() {
        if (properties.accessKeyId() == null || properties.accessKeyId().isBlank()
                || properties.accessKeySecret() == null || properties.accessKeySecret().isBlank()
                || properties.bucket() == null || properties.bucket().isBlank()
                || properties.endpoint() == null || properties.endpoint().isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "阿里云 OSS 配置未完成");
        }
        return new OSSClientBuilder().build(properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret());
    }

    public record StoredObject(java.io.InputStream stream, long length) {}
}
