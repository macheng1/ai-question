package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.Map;

/** 按对象来源路由存储；无前缀的历史视频仍读取本地文件。 */
@Service
public class TrainingVideoStorage {
    private final FileStorageService oss;
    private final Path legacyRoot;
    public TrainingVideoStorage(FileStorageService oss,
            @Value("${training.video-directory:./data/training-videos}") String legacyDirectory) {
        this.oss = oss;
        this.legacyRoot = Path.of(legacyDirectory).toAbsolutePath().normalize();
    }
    public String upload(MultipartFile file, String contentType) {
        return oss.uploadMaterial(file, contentType, "video/mp4".equals(contentType) ? "mp4" : "webm", "training-videos");
    }
    public ResponseEntity<Resource> content(Map<String, Object> row) {
        String key = row.get("storage_path").toString();
        var response = ResponseEntity.ok().contentType(MediaType.parseMediaType(row.get("content_type").toString()))
                .header("Cache-Control", "private, no-store");
        if ((key.startsWith("oss:") || key.startsWith("eds:"))) {
            var object = oss.open(validatedKey(key));
            return response.contentLength(object.length()).body(new InputStreamResource(object.stream()));
        }
        Path file = legacyFile(key);
        if (!Files.isRegularFile(file)) throw new BusinessException(ErrorCode.NOT_FOUND, "历史视频文件不存在，请重新上传至 OSS");
        return response.body(new FileSystemResource(file));
    }
    public void delete(String key) {
        if ((key.startsWith("oss:") || key.startsWith("eds:"))) { oss.delete(validatedKey(key)); return; }
        try { Files.deleteIfExists(legacyFile(key)); }
        catch (java.io.IOException ex) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "历史视频清理失败"); }
    }
    private String validatedKey(String storedKey) {
        String key = storedKey.substring(4);
        if (!key.matches("training-videos/[a-f0-9-]{36}\\.(mp4|webm)")) throw new BusinessException(ErrorCode.BAD_REQUEST, "视频存储键无效");
        return storedKey;
    }
    private Path legacyFile(String key) {
        Path file = legacyRoot.resolve(key).normalize();
        if (!file.startsWith(legacyRoot)) throw new BusinessException(ErrorCode.BAD_REQUEST, "视频路径无效");
        return file;
    }
}
