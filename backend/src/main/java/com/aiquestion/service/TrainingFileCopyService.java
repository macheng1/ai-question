package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.common.VideoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aiquestion.storage.FileStorageService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 为副本创建独立存储对象。 */
@Service
public class TrainingFileCopyService {
  private static final Logger log = LoggerFactory.getLogger(TrainingFileCopyService.class);
  private final FileStorageService storage;
  public TrainingFileCopyService(FileStorageService storage) {
    this.storage = storage;
  }

  public String copyMaterial(Map<String, Object> material) {
    String key = material.get("storage_key").toString();
    String suffix = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    String mime = "PDF".equals(material.get("material_type")) ? "application/pdf"
        : "png".equals(suffix) ? "image/png" : "image/jpeg";
    var object = storage.open(key);
    try (InputStream input = object.stream()) {
      return copy(input, mime, suffix);
    } catch (IOException ex) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "培训材料复制失败，请重试");
    }
  }

  private String copy(InputStream input, String mime, String suffix) throws IOException {
    Path temp = Files.createTempFile("training-copy-", "." + suffix);
    try {
      long bytes = Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
      return storage.uploadMaterial(new TemporaryUpload(temp, mime, bytes), mime, suffix, "training-materials");
    } finally {
      try { Files.deleteIfExists(temp); }
      catch (IOException ex) {
        log.warn("培训材料复制临时文件清理失败 error={}", VideoLog.error(ex));
      }
    }
  }

  private record TemporaryUpload(Path path, String mime, long size) implements MultipartFile {
    public String getName() { return "file"; }
    public String getOriginalFilename() { return path.getFileName().toString(); }
    public String getContentType() { return mime; }
    public boolean isEmpty() { return size == 0; }
    public long getSize() { return size; }
    public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
    public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
    public void transferTo(File target) throws IOException {
      Files.copy(path, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
