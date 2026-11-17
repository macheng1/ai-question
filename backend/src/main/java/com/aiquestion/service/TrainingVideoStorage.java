package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.Map;

/** 按对象来源路由存储；无前缀的历史视频仍读取本地文件。 */
@Service
public class TrainingVideoStorage {
    private static final Logger log = LoggerFactory.getLogger(TrainingVideoStorage.class);
    private final FileStorageService oss;
    private final Path legacyRoot;
    private final TrainingVideoCache cache;
    public TrainingVideoStorage(FileStorageService oss,
            @Value("${training.video-directory:./data/training-videos}") String legacyDirectory, TrainingVideoCache cache) {
        this.cache = cache;
        this.oss = oss;
        this.legacyRoot = Path.of(legacyDirectory).toAbsolutePath().normalize();
    }
    public String upload(MultipartFile file, String contentType) {
        long started = System.nanoTime();
        log.info("video event=storage_upload_start bytes={}", file.getSize());
        String key;
        try {
            key = validatedKey(oss.uploadMaterial(file, contentType, "video/mp4".equals(contentType) ? "mp4" : "webm", "training-videos"));
        } catch (RuntimeException ex) {
            log.warn("video event=storage_upload_failed bytes={} elapsedMs={} errorType={}",
                    file.getSize(), VideoLog.elapsedMs(started), ex.getClass().getSimpleName());
            throw ex;
        }
        log.info("video event=storage_uploaded key={} bytes={} elapsedMs={}", key, file.getSize(), VideoLog.elapsedMs(started));
        cache.warm(key, file);
        return key;
    }
    String reserve(String type) { return oss.reserveVideo(type); }

    void putReserved(String key, MultipartFile file) { oss.putReservedVideo(validatedKey(key), file); }

    void warmReserved(String key, MultipartFile file) {
        if (!cache.warm(validatedKey(key), file))
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "视频已存储，播放缓存暂不可用，请稍后重试");
    }

    public ResponseEntity<Resource> content(Map<String, Object> row) {
        String key = row.get("storage_path").toString();
        var response = ResponseEntity.ok().contentType(MediaType.parseMediaType(row.get("content_type").toString()))
                .header("Cache-Control", "private, no-store");
        if (key.startsWith("oss:") || key.startsWith("eds:")) {
            validatedKey(key);
            try {
                return response.body(cache.resource(key, () -> oss.open(key)));
            } catch (java.io.IOException ex) {
                log.warn("video event=playback_resource_failed key={} errorType={}", key, ex.getClass().getSimpleName());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "视频读取失败，请重试");
            }
        }
        Path file = legacyFile(key);
        if (!Files.isRegularFile(file)) {
            log.info("video event=legacy_video_missing");
            throw new BusinessException(ErrorCode.NOT_FOUND, "历史视频文件不存在，请重新上传至 OSS");
        }
        log.info("video event=playback_resource source=legacy");
        return response.body(new FileSystemResource(file));
    }
    void deleteAfterCommit(String key, String reason) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try {
                        delete(key);
                        log.info("video event=object_cleanup object={} reason={} result=ok", VideoLog.id(key), reason);
                    } catch (RuntimeException ex) {
                        log.warn("video event=object_cleanup object={} reason={} result=failed error={}", VideoLog.id(key), reason, VideoLog.error(ex));
                    }
                }
            });
    }

    void abortMultipart(String reference, String upload) {
        String key = validatedKey(reference).substring(4);
        var provider = oss.videoMultipart(reference);
        if (upload != null) {
            provider.abortVideo(key, upload);
        } else {
            // An initiation may have succeeded just before a process exited or the response was lost.
            for (String pending : provider.pendingVideoUploads(key)) provider.abortVideo(key, pending);
        }
    }

    public void delete(String key) {
        if (key.startsWith("oss:") || key.startsWith("eds:")) {
            validatedKey(key);
            cache.delete(key, () -> oss.delete(key));
            return;
        }
        try {
            boolean removed = Files.deleteIfExists(legacyFile(key));
            log.info("video event=legacy_delete removed={}", removed);
        } catch (java.io.IOException ex) {
            log.warn("video event=legacy_delete_failed errorType={}", ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "历史视频清理失败");
        }
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
