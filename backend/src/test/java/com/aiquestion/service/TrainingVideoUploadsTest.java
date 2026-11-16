package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class TrainingVideoUploadsTest {
    @TempDir Path directory;
    TrainingVideoUploads service;
    @BeforeEach void setup() { service = new TrainingVideoUploads(directory.toString(), 8L << 30, 2L << 30); }

    @Test void acceptsTwoGiBAndUsesLongOffsetsForLastPart() throws Exception {
        long limit = 1L << 31;
        String id = service.create(1, "片尾.webm", limit);
        byte[] last = new byte[TrainingVideoUploads.PART_SIZE];
        service.put(1, id, 255, new ByteArrayInputStream(last));
        assertEquals(limit, Files.size(directory.resolve(id + ".part")));
        assertThrows(BusinessException.class, () -> service.put(1, id, 256, new ByteArrayInputStream(last)));
        assertThrows(BusinessException.class, () -> service.create(1, "大.mp4", limit + 1));
        assertThrows(BusinessException.class, () -> service.create(1, "短.mp4", 11));
        assertThrows(BusinessException.class, () -> service.create(1, "错误.mov", 12));
    }

    @Test void ownerQuotaCountsReservedSizes() throws Exception {
        for (int i = 0; i < 2; i++) service.create(1, "培训.mp4", 1L << 31);
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, assertThrows(BusinessException.class,
                () -> service.create(1, "培训.mp4", 12)).getErrorCode());
        service.create(2, "培训.mp4", 12);
    }

    @Test void refusesCreationWhenFreeSpaceWouldFallBelowReserve() throws Exception {
        var full = new TrainingVideoUploads(directory.toString(), 8L << 30, Files.getFileStore(directory).getUsableSpace());
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, assertThrows(BusinessException.class,
                () -> full.create(1, "培训.mp4", 1L << 30)).getErrorCode());
    }

    @Test void resolvesOutOfOrderPartsAndReusesTicketOnlyAfterRollback() throws Exception {
        byte[] first = new byte[TrainingVideoUploads.PART_SIZE];
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, first, 4, 4);
        String id = service.create(7, "教学.MP4", first.length + 1L);
        var descriptor = new MockMultipartFile("videos", "ticket", TrainingVideoUploads.DESCRIPTOR,
                id.getBytes(StandardCharsets.US_ASCII));
        assertEquals(ErrorCode.VIDEO_UPLOAD_EXPIRED, assertThrows(BusinessException.class,
                () -> service.put(8, id, 1, new ByteArrayInputStream(new byte[]{1}))).getErrorCode());
        assertThrows(BusinessException.class, () -> service.put(7, id, 1, new ByteArrayInputStream(new byte[2])));
        service.put(7, id, 1, new ByteArrayInputStream(new byte[]{1}));
        service.put(7, id, 1, new ByteArrayInputStream(new byte[]{1}));
        assertThrows(BusinessException.class, () -> service.put(7, id, 1, new ByteArrayInputStream(new byte[]{2})));
        assertThrows(BusinessException.class, () -> service.resolve(7, descriptor));
        assertThrows(BusinessException.class, () -> service.put(7, id, 0, new ByteArrayInputStream(new byte[first.length])));
        service.put(7, id, 0, new ByteArrayInputStream(first));
        for (int status : new int[]{TransactionSynchronization.STATUS_ROLLED_BACK, TransactionSynchronization.STATUS_COMMITTED}) {
            TransactionSynchronizationManager.initSynchronization();
            try {
                var staged = service.resolve(7, descriptor);
                assertEquals("教学.MP4", staged.getOriginalFilename());
                try (var input = staged.getInputStream()) {
                    assertArrayEquals(first, input.readNBytes(first.length));
                    assertEquals(1, input.read());
                    assertEquals(-1, input.read());
                }
                assertThrows(BusinessException.class, () -> service.resolve(7, descriptor));
                TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
        }
        assertFalse(Files.exists(directory.resolve(id + ".part")));
        assertThrows(BusinessException.class, () -> service.resolve(7, descriptor));
    }
}
