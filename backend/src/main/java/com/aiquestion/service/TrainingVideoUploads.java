package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TrainingVideoUploads {
    public static final String DESCRIPTOR = "application/vnd.aiquestion.video-upload";
    static final long MAX_SIZE = 2L * 1024 * 1024 * 1024;
    static final int PART_SIZE = 8 * 1024 * 1024;
    private static final long TTL = Duration.ofHours(24).toMillis();

    private final Path directory;
    private final long capacity;
    private final long reserve;
    private long writingBytes;
    private final Map<String, Upload> uploads = new ConcurrentHashMap<>();

    public TrainingVideoUploads(@Value("${training.video-upload-directory:${java.io.tmpdir}/aiquestion-video-uploads}") String directory,
            @Value("${training.video-upload-max-bytes:8589934592}") long capacity,
            @Value("${training.video-upload-reserve-bytes:2147483648}") long reserve) {
        this.directory = Path.of(directory);
        this.capacity = capacity;
        this.reserve = reserve;
    }

    private static final class Upload {
        final long owner;
        final String name;
        final long size;
        final Path path;
        final byte[][] digests;
        long touched = System.currentTimeMillis();
        boolean claimed;

        Upload(long owner, String name, long size, Path path) {
            this.owner = owner;
            this.name = name;
            this.size = size;
            this.path = path;
            digests = new byte[(int) ((size + PART_SIZE - 1) / PART_SIZE)][];
        }
    }

    public synchronized String create(long owner, String name, long size) throws IOException {
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(Character::isISOControl))
            throw bad("视频文件名无效");
        type(name, size);
        long reserved = 0, ownerReserved = 0;
        int ownerCount = 0;
        for (Upload upload : uploads.values()) {
            reserved += upload.size;
            if (upload.owner == owner) {
                ownerReserved += upload.size;
                ownerCount++;
            }
        }
        if (ownerCount >= 64 || uploads.size() >= 512 || ownerReserved + size > Math.max(MAX_SIZE, capacity / 2)
                || reserved + size > capacity)
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "暂存视频已达容量限制，请先保存已上传的视频");
        Files.createDirectories(directory);
        if (Files.getFileStore(directory).getUsableSpace() - reserve - writingBytes < size)
            throw insufficientSpace();
        String id = UUID.randomUUID().toString();
        Path path = Files.createFile(directory.resolve(id + ".part"));
        uploads.put(id, new Upload(owner, name, size, path));
        return id;
    }

    public void put(long owner, String id, int index, InputStream input) throws IOException {
        Upload upload = owned(owner, id);
        synchronized (upload) {
            checkAvailable(id, upload);
            if (index < 0 || index >= upload.digests.length) throw bad("视频分片序号无效");
            long offset = (long) index * PART_SIZE;
            int remaining = (int) Math.min(PART_SIZE, upload.size - offset);
            byte[] previous = upload.digests[index];
            MessageDigest digest = sha256();
            int writeBudget = previous == null ? remaining : 0;
            if (writeBudget > 0) reserveWrite(writeBudget);
            try (var output = new RandomAccessFile(upload.path.toFile(), "rw")) {
                output.seek(offset);
                byte[] buffer = new byte[64 * 1024];
                while (remaining > 0) {
                    int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (count == -1) throw bad("视频分片长度错误");
                    digest.update(buffer, 0, count);
                    if (previous == null) output.write(buffer, 0, count);
                    remaining -= count;
                }
                if (input.read() != -1) throw bad("视频分片长度错误");
                byte[] hash = digest.digest();
                if (previous != null && !Arrays.equals(hash, previous)) throw bad("已上传的分片内容不同");
                if (index == 0 && previous == null) {
                    byte[] head = new byte[12];
                    output.seek(0);
                    output.readFully(head);
                    verifyHeader(type(upload.name, upload.size), head);
                }
                upload.digests[index] = hash;
                upload.touched = System.currentTimeMillis();
            } finally {
                if (writeBudget > 0) releaseWrite(writeBudget);
            }
        }
    }

    private synchronized void reserveWrite(int bytes) throws IOException {
        if (Files.getFileStore(directory).getUsableSpace() - reserve - writingBytes < bytes)
            throw insufficientSpace();
        writingBytes += bytes;
    }

    private synchronized void releaseWrite(int bytes) { writingBytes -= bytes; }

    private static BusinessException insufficientSpace() {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "视频暂存空间不足，请稍后重试");
    }

    public MultipartFile resolve(long owner, MultipartFile descriptor) throws IOException {
        if (!DESCRIPTOR.equalsIgnoreCase(descriptor.getContentType())) return descriptor;
        if (descriptor.getSize() != 36) throw bad("无效的视频暂存标识");
        String id = new String(descriptor.getBytes(), StandardCharsets.US_ASCII);
        Upload upload = owned(owner, id);
        synchronized (upload) {
            checkAvailable(id, upload);
            for (byte[] digest : upload.digests) if (digest == null) throw bad("视频尚未上传完成");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    synchronized (upload) {
                        if (status == STATUS_COMMITTED) discard(id, upload);
                        upload.claimed = false;
                        upload.touched = System.currentTimeMillis();
                    }
                }
            });
            upload.claimed = true;
            return new StagedFile(upload);
        }
    }

    static String type(String name, long size) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (size < 12 || size > MAX_SIZE || !(lower.endsWith(".mp4") || lower.endsWith(".webm")))
            throw bad("仅支持2GB以内的MP4或WebM视频");
        return lower.endsWith(".mp4") ? "video/mp4" : "video/webm";
    }

    static void verifyHeader(String type, byte[] head) {
        if (head.length < 12 || !("video/mp4".equals(type)
                ? head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p'
                : (head[0] & 255) == 0x1a && head[1] == 0x45 && (head[2] & 255) == 0xdf && (head[3] & 255) == 0xa3))
            throw bad("视频文件内容与扩展名不匹配");
    }

    private Upload owned(long owner, String id) {
        Upload upload = uploads.get(id);
        if (upload == null || upload.owner != owner) throw new BusinessException(ErrorCode.VIDEO_UPLOAD_EXPIRED);
        return upload;
    }

    private void checkAvailable(String id, Upload upload) {
        if (upload.claimed) throw bad("视频正在保存");
        if (uploads.get(id) != upload || System.currentTimeMillis() - upload.touched >= TTL)
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_EXPIRED);
    }

    @Scheduled(fixedDelay = 600000)
    public void expire() throws IOException {
        long cutoff = System.currentTimeMillis() - TTL;
        uploads.forEach((id, upload) -> {
            synchronized (upload) {
                if (!upload.claimed && upload.touched <= cutoff) discard(id, upload);
            }
        });
        // A restart loses upload tickets. Reclaim abandoned files when their last write expires.
        if (Files.isDirectory(directory)) try (var files = Files.newDirectoryStream(directory, "*.part")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!name.matches("[0-9a-f-]{36}\\.part") || uploads.containsKey(name.substring(0, 36))) continue;
                try {
                    if (Files.getLastModifiedTime(file).toMillis() <= cutoff) Files.deleteIfExists(file);
                } catch (NoSuchFileException ignored) { }
            }
        }
    }

    private void discard(String id, Upload upload) {
        uploads.remove(id, upload);
        try { Files.deleteIfExists(upload.path); }
        catch (IOException error) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("无法清理培训视频暂存文件 {}", id, error);
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }

    private record StagedFile(Upload upload) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return upload.name; }
        public String getContentType() { return type(upload.name, upload.size); }
        public boolean isEmpty() { return false; }
        public long getSize() { return upload.size; }
        public byte[] getBytes() throws IOException { throw new IOException("培训视频应使用流式读取"); }
        public InputStream getInputStream() throws IOException { return Files.newInputStream(upload.path); }
        public void transferTo(File target) throws IOException { Files.copy(upload.path, target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }
}
