package com.aiquestion.upload;

import com.aiquestion.storage.FileStorageService;
import java.util.Set;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 普通附件随机地址；培训材料仍走独立鉴权接口。 */
@RestController
@RequestMapping("/common/files")
public class PublicFileController {
  private final FileStorageService storage;
  public PublicFileController(FileStorageService storage) { this.storage = storage; }
  @GetMapping("/{provider}/{id}")
  public ResponseEntity<Resource> content(@PathVariable String provider, @PathVariable String id) {
    var object = storage.openPublicFile(provider, id);
    String mime = Set.of("image/png", "image/jpeg", "image/gif", "image/webp")
        .contains(object.contentType() == null ? "" : object.contentType())
        ? object.contentType() : "application/octet-stream";
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime))
        .contentLength(object.length()).header("X-Content-Type-Options", "nosniff")
        .header("Content-Disposition", mime.startsWith("image/") ? "inline" : "attachment")
        .header("Cache-Control", "private, max-age=300")
        .body(new InputStreamResource(object.stream()));
  }
}
