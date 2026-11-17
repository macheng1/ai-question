package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.common.VideoLog;
import com.aiquestion.repository.PreparedVideoRepository;
import com.aiquestion.repository.PreparedVideoRepository.Ticket;
import com.aiquestion.storage.FileStorageService;
import com.aiquestion.storage.VideoMultipartStorage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DirectTrainingVideoUploads {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DirectTrainingVideoUploads.class);
    private static final java.util.Set<String> STORAGE_ERRORS = java.util.Set.of("AccessDenied", "InvalidAccessKeyId",
            "SignatureDoesNotMatch", "NoSuchBucket", "NoSuchKey", "NoSuchUpload", "InvalidPart", "InvalidPartOrder",
            "EntityTooSmall", "InvalidArgument", "RequestTimeout", "InternalError", "ServiceUnavailable", "SlowDown");
    private static final int OSS_PART_UNIT = 512 * 1024;
    private static final int MAX_LISTED_PARTS = 1000;
    private final PreparedVideoRepository tickets;
    private final FileStorageService storage;
    private final TransactionTemplate transaction;
    private final boolean browserDirect;
    // Limit parallel proxy buffers to 64 MiB per instance. Browser-to-bucket uploads do not use these slots.
    private final java.util.concurrent.Semaphore proxySlots = new java.util.concurrent.Semaphore(8);

    public DirectTrainingVideoUploads(PreparedVideoRepository tickets, FileStorageService storage,
            TransactionTemplate transaction, @Value("${training.video-browser-direct:false}") boolean browserDirect) {
        this.tickets = tickets;
        this.storage = storage;
        this.transaction = transaction;
        this.browserDirect = browserDirect;
    }

    public record Session(String id, int partSize, boolean browserDirect) {}

    public Session create(long owner, String name, long size) {
        TrainingVideoUploads.validate(name, size);
        String type = TrainingVideoUploads.type(name, size);
        String reference = storage.reserveVideo(type);
        VideoMultipartStorage provider = storage.videoMultipart(reference);
        if (provider == null) return new Session(null, 0, false);
        String id = UUID.randomUUID().toString();
        int partSize = choosePartSize(reference, size);
        var ticket = new Ticket(id, owner, name, size, reference, "DIRECT", deadline(), partSize);
        // Persist the unique key before contacting storage so an interrupted initiation can be cleaned up.
        transaction.executeWithoutResult(tx -> {
            tickets.lockOwner(owner);
            if (tickets.pendingCount(owner) >= 64)
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "暂存视频已达数量限制，请先保存已上传的视频");
            tickets.reserve(ticket);
        });
        logged("create", ticket, -1, () -> {
            String upload = provider.beginVideo(key(ticket), type);
            tickets.attach(id, upload);
            return null;
        });
        return new Session(id, partSize, browserDirect);
    }

    public String sign(long owner, String id, int index) {
        if (!browserDirect) throw bad("当前使用服务端分片上传");
        Ticket ticket = partTicket(owner, id, index);
        return logged("sign", ticket, index, () ->
                storage.videoMultipart(ticket.key()).signVideoPart(key(ticket), upload(id), index + 1));
    }

    public void put(long owner, String id, int index, long bytes, InputStream input) throws IOException {
        Ticket ticket = partTicket(owner, id, index);
        long expected = partBytes(ticket.size(), ticket.partSize(), index);
        if (bytes != expected) throw bad("视频分片长度错误");
        String upload = upload(id);
        if (!proxySlots.tryAcquire())
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "视频分片上传繁忙，请稍后重试");
        try {
            byte[] buffer = new byte[(int) expected];
            if (input.readNBytes(buffer, 0, buffer.length) != buffer.length || input.read() != -1)
                throw bad("视频分片长度错误");
            if (index == 0)
                TrainingVideoUploads.verifyHeader(TrainingVideoUploads.type(ticket.name(), ticket.size()), buffer);
            // A replayable part allows SDK retries after the object's response was lost.
            logged("put", ticket, index, () -> {
                putOrVerify(ticket, upload, index + 1, buffer);
                return null;
            });
        } finally {
            proxySlots.release();
        }
    }

    private void putOrVerify(Ticket ticket, String upload, int number, byte[] bytes) {
        var provider = storage.videoMultipart(ticket.key());
        try {
            provider.putVideoPart(key(ticket), upload, number, new java.io.ByteArrayInputStream(bytes), bytes.length);
        } catch (com.aliyun.oss.ClientException | com.amazonaws.AmazonClientException failure) {
            if (failure instanceof com.amazonaws.AmazonServiceException) throw failure;
            // A transport failure can occur after a successful write. A matching length alone is insufficient.
            String digest = org.springframework.util.DigestUtils.md5DigestAsHex(bytes);
            try {
                var parts = provider.videoParts(key(ticket), upload);
                for (var part : parts) {
                    if (part.number() == number && part.bytes() == bytes.length && digest.equalsIgnoreCase(part.etag())) {
                        log.info("video event=multipart_part_verified upload={} index={}", VideoLog.id(ticket.id()), number - 1);
                        return;
                    }
                }
            } catch (RuntimeException verification) {
                failure.addSuppressed(verification);
            }
            throw failure;
        }
    }

    public void complete(long owner, String id) {
        // Only completion/consumption/cleanup serialize on this row. Part uploads stream independently.
        transaction.executeWithoutResult(tx -> {
            Ticket ticket = owned(tickets.lock(id), owner);
            String upload = upload(id);
            if ("READY".equals(ticket.phase())) return;
            if (!"DIRECT".equals(ticket.phase())) throw bad("视频上传会话类型无效");
            logged("complete", ticket, -1, () -> {
                finish(ticket, upload);
                tickets.advance(id, "READY", deadline());
                return null;
            });
        });
    }

    private void finish(Ticket ticket, String upload) {
        VideoMultipartStorage provider = storage.videoMultipart(ticket.key());
        String key = key(ticket);
        // A response or transaction commit can be lost after storage has already completed the upload.
        if (!provider.videoExists(key)) {
            var parts = provider.videoParts(key, upload);
            validateParts(ticket.size(), ticket.partSize(), parts);
            provider.finishVideo(key, upload, parts);
        }
        if (provider.videoLength(key) != ticket.size()) throw bad("视频文件大小不匹配");
        try {
            TrainingVideoUploads.verifyHeader(TrainingVideoUploads.type(ticket.name(), ticket.size()), provider.videoHeader(key));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取视频文件头失败，请重试");
        }
    }

    private static <T> T logged(String action, Ticket ticket, int index, java.util.function.Supplier<T> operation) {
        long start = System.nanoTime();
        String provider = ticket.key().startsWith("oss:") ? "aliyun" : "eds";
        String ref = VideoLog.id(ticket.id());
        try {
            T result = operation.get();
            log.info("video event=multipart action={} provider={} upload={} index={} result=ok elapsedMs={}",
                    action, provider, ref, index, VideoLog.elapsedMs(start));
            return result;
        } catch (RuntimeException ex) {
            String code = null;
            if (ex instanceof com.aliyun.oss.OSSException oss) code = oss.getErrorCode();
            if (ex instanceof com.amazonaws.AmazonServiceException aws) code = aws.getErrorCode();
            log.warn("video event=multipart action={} provider={} upload={} index={} result=failed elapsedMs={} error={} sdkCode={}",
                    action, provider, ref, index, VideoLog.elapsedMs(start), VideoLog.error(ex),
                    code != null && STORAGE_ERRORS.contains(code) ? code : "-");
            throw ex;
        }
    }

    static int choosePartSize(String reference, long size) {
        if (!reference.startsWith("oss:")) return TrainingVideoUploads.PART_SIZE;
        long bytesPerUnit = (long) OSS_PART_UNIT * MAX_LISTED_PARTS;
        return Math.toIntExact(((size + bytesPerUnit - 1) / bytesPerUnit) * OSS_PART_UNIT);
    }

    static void validateParts(long size, int partSize, List<VideoMultipartStorage.Part> parts) {
        if (parts.size() != (size + partSize - 1) / partSize) throw bad("视频尚未上传完成");
        for (int index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            if (part.number() != index + 1 || part.bytes() != partBytes(size, partSize, index) || part.etag() == null)
                throw bad("视频分片信息与声明不符");
        }
    }

    private Ticket partTicket(long owner, String id, int index) {
        Ticket ticket = owned(tickets.find(id), owner);
        if (!"DIRECT".equals(ticket.phase())) throw bad("当前视频已结束上传");
        if (index < 0 || (long) index * ticket.partSize() >= ticket.size()) throw bad("视频分片序号无效");
        return ticket;
    }

    private String upload(String id) {
        String upload = tickets.multipartId(id);
        if (upload == null) throw bad("分片上传尚未就绪");
        return upload;
    }

    private static Ticket owned(Ticket ticket, long owner) {
        if (ticket == null || ticket.owner() != owner || ticket.expires() <= System.currentTimeMillis())
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_EXPIRED);
        return ticket;
    }

    private static String key(Ticket ticket) { return ticket.key().substring(4); }
    private static long deadline() { return System.currentTimeMillis() + Duration.ofHours(24).toMillis(); }
    private static long partBytes(long size, int partSize, int index) { return Math.min(partSize, size - (long) index * partSize); }
    private static BusinessException bad(String text) { return new BusinessException(ErrorCode.BAD_REQUEST, text); }
}
