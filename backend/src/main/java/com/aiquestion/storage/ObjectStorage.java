package com.aiquestion.storage;

import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/** 存储厂商适配接口；权限、文件命名和来源路由由业务层统一处理。 */
public interface ObjectStorage {
  String provider();
  void validateConfiguration();
  void put(MultipartFile file, String contentType, String key);
  StoredObject open(String key);
  void delete(String key);
  default java.util.Optional<String> directPreviewUrl(String key) { return java.util.Optional.empty(); }

  record StoredObject(InputStream stream, long length, String contentType) {}
}
