package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.common.VideoLog;
import com.aiquestion.repository.PreparedVideoRepository;
import com.aiquestion.repository.PreparedVideoRepository.Ticket;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PreparedTrainingVideos {
    private static final Logger log = LoggerFactory.getLogger(PreparedTrainingVideos.class);
    private static final long TTL = Duration.ofHours(24).toMillis();
    private final TrainingVideoUploads staging;
    private final PreparedVideoRepository repository;
    private final TrainingVideoStorage storage;
    private final TransactionTemplate transaction;

    public PreparedTrainingVideos(TrainingVideoUploads staging, PreparedVideoRepository repository,
            TrainingVideoStorage storage, TransactionTemplate transaction) {
        this.staging = staging;
        this.repository = repository;
        this.storage = storage;
        this.transaction = transaction;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void prepare(long owner, String id) {
        long start = System.nanoTime();
        try {
            Ticket existing = repository.find(id);
            if (existing != null && "READY".equals(owned(existing, owner).phase())) return;
            staging.prepare(owner, id, file -> {
                // Commit the destination key before PUT so cleanup survives a process crash.
                if (repository.find(id) == null) {
                    repository.reserve(new Ticket(id, owner, file.getOriginalFilename(), file.getSize(),
                            storage.reserve(file.getContentType()), "NEW", deadline()));
                }
                transaction.executeWithoutResult(tx -> {
                    Ticket ticket = owned(repository.lock(id), owner);
                    if ("NEW".equals(ticket.phase())) {
                        storage.putReserved(ticket.key(), file);
                        repository.advance(id, "STORED", deadline());
                    }
                });
                transaction.executeWithoutResult(tx -> {
                    Ticket ticket = owned(repository.lock(id), owner);
                    if ("STORED".equals(ticket.phase())) {
                        storage.warmReserved(ticket.key(), file);
                        repository.advance(id, "READY", deadline());
                    }
                });
            });
            log.info("video event=upload_prepare upload={} result=ready elapsedMs={}", VideoLog.id(id), VideoLog.elapsedMs(start));
        } catch (RuntimeException ex) {
            // A concurrent prepare can finish and release the local file before this request acquires it.
            if (ex instanceof BusinessException business && business.getErrorCode() == ErrorCode.VIDEO_UPLOAD_EXPIRED) {
                Ticket ticket = repository.find(id);
                if (ticket != null && ticket.owner() == owner && ticket.expires() > System.currentTimeMillis()
                        && "READY".equals(ticket.phase())) return;
            }
            log.warn("video event=upload_prepare upload={} result=failed error={} elapsedMs={}",
                    VideoLog.id(id), VideoLog.error(ex), VideoLog.elapsedMs(start));
            throw ex;
        }
    }

    public record Progress(long storedBytes, long totalBytes) {}

    public Progress progress(long owner, String id) {
        var local = staging.progress(owner, id);  // No staging or database lock during a long write.
        if (local != null) return new Progress(local.storedBytes(), local.totalBytes());
        Ticket ticket = owned(repository.find(id), owner);
        return new Progress("NEW".equals(ticket.phase()) ? 0 : ticket.size(), ticket.size());
    }

    /** Ticket removal and the training_video insert must commit or roll back together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Ticket consume(long owner, MultipartFile descriptor) throws IOException {
        String id = TrainingVideoUploads.descriptorId(descriptor);
        if (id == null || repository.find(id) == null) return null;
        Ticket ticket = owned(repository.lock(id), owner);
        if (!"READY".equals(ticket.phase()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频尚未准备完成，请稍后重试");
        repository.remove(id);
        return ticket;
    }

    @Scheduled(fixedDelay = 600000)
    public void expire() {
        for (String id : repository.expired(System.currentTimeMillis())) {
            try {
                transaction.executeWithoutResult(tx -> {
                    Ticket ticket = repository.lock(id);
                    if (ticket == null || ticket.expires() > System.currentTimeMillis()) return;
                    if ("DIRECT".equals(ticket.phase())) storage.abortMultipart(ticket.key(), repository.multipartId(id));
                    storage.delete(ticket.key());
                    repository.remove(id);
                });
            } catch (RuntimeException ex) {
                log.warn("video event=prepared_cleanup upload={} result=failed error={}", VideoLog.id(id), VideoLog.error(ex));
            }
        }
    }

    private static Ticket owned(Ticket ticket, long owner) {
        if (ticket == null || ticket.owner() != owner || ticket.expires() <= System.currentTimeMillis())
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_EXPIRED);
        return ticket;
    }

    private static long deadline() { return System.currentTimeMillis() + TTL; }
}
