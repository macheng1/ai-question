package com.aiquestion.storage;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.integration.OssUploadResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 新文件使用部署选择的存储；旧文件按其来源读取，绝不静默切换厂商。 */
@Service
public class FileStorageService {
  private final Map<String, ObjectStorage> providers;
  private final ObjectStorage active;
  @Value("${storage.public-base-url:}")
  private String publicBaseUrl;

  public FileStorageService(@Value("${storage.provider:eds}") String provider,
      List<ObjectStorage> providers) {
    this.providers = providers.stream().collect(Collectors.toMap(ObjectStorage::provider, Function.identity()));
    this.active = requireProvider(provider);
    // 历史阿里云部署允许不配置上传。显式启用 EDS 时必须具备完整连接配置。
    if ("eds".equals(provider)) active.validateConfiguration();
  }

  public String uploadMaterial(MultipartFile file, String mime, String suffix, String folder) {
    if (!Set.of("mp4", "webm", "pdf", "png", "jpg").contains(suffix)
        || !Set.of("training-videos", "training-materials").contains(folder)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "培训材料类型无效");
    }
    String key = folder + "/" + UUID.randomUUID() + "." + suffix;
    active.put(file, mime, key);
    return ("aliyun".equals(active.provider()) ? "oss:" : "eds:") + key;
  }

  public String reservePreview(String original) {
    return ("aliyun".equals(providerFor(original).provider()) ? "oss:" : "eds:")
        + "training-previews/" + UUID.randomUUID() + ".png";
  }

  public void putPreview(String reference, byte[] bytes) {
    if (!objectKey(reference).startsWith("training-previews/")) throw new IllegalArgumentException("Invalid preview key");
    MultipartFile file = new MultipartFile() {
      public String getName() { return "file"; }
      public String getOriginalFilename() { return "page.png"; }
      public String getContentType() { return "image/png"; }
      public boolean isEmpty() { return bytes.length == 0; }
      public long getSize() { return bytes.length; }
      public byte[] getBytes() { return bytes; }
      public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
      public void transferTo(java.io.File dest) throws java.io.IOException { java.nio.file.Files.write(dest.toPath(), bytes); }
    };
    providerFor(reference).put(file, "image/png", objectKey(reference));
  }

  public OssUploadResult upload(MultipartFile file) {
    // 普通附件沿用公开链接语义，仅开放单独的随机键目录，不暴露培训私有材料。
    String id = UUID.randomUUID().toString();
    String key = "common-files/" + id;
    String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    active.put(file, mime, key);
    var builder = publicBaseUrl == null || publicBaseUrl.isBlank()
        ? ServletUriComponentsBuilder.fromCurrentContextPath()
        : org.springframework.web.util.UriComponentsBuilder.fromUriString(publicBaseUrl);
    String url = builder
        .path("/common/files/").path(active.provider()).path("/").path(id)
        .toUriString();
    return new OssUploadResult(active.provider() + ":" + key, file.getOriginalFilename(),
        url, file.getSize(), mime);
  }

  public ObjectStorage.StoredObject open(String reference) {
    return providerFor(reference).open(objectKey(reference));
  }

  public java.util.Optional<String> directPreviewUrl(String reference) {
    String key = objectKey(reference);
    if (!key.startsWith("training-previews/")) return java.util.Optional.empty();
    return providerFor(reference).directPreviewUrl(key);
  }

  public void delete(String reference) {
    providerFor(reference).delete(objectKey(reference));
  }

  public ObjectStorage.StoredObject openPublicFile(String provider, String id) {
    if (!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "文件标识无效");
    }
    return requireProvider(provider).open("common-files/" + id);
  }

  private ObjectStorage providerFor(String reference) {
    if (reference.startsWith("oss:")) return requireProvider("aliyun");
    int colon = reference.indexOf(':');
    // 历史 PDF/图片只记录对象键，均来自阿里云。
    return requireProvider(colon < 0 ? "aliyun" : reference.substring(0, colon));
  }

  private String objectKey(String reference) {
    int colon = reference.indexOf(':');
    return colon < 0 ? reference : reference.substring(colon + 1);
  }

  private ObjectStorage requireProvider(String provider) {
    ObjectStorage storage = providers.get(provider);
    if (storage == null) throw new IllegalArgumentException("不支持的存储类型: " + provider);
    return storage;
  }
}
