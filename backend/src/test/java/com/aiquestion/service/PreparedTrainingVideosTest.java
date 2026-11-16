package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.PreparedVideoRepository;
import com.aiquestion.repository.PreparedVideoRepository.Ticket;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreparedTrainingVideosTest {
    @TempDir Path directory;

    @Test void progressDuringSlowWriteAndWarmRetryDoesNotReupload() throws Exception {
        var staging = new TrainingVideoUploads(directory.toString(), 8L << 30, 0);
        byte[] video = {0, 0, 0, 12, 'f', 't', 'y', 'p', 0, 0, 0, 0};
        String id = staging.create(3, "video.mp4", video.length);
        staging.put(3, id, 0, new ByteArrayInputStream(video));
        var repo = mock(PreparedVideoRepository.class);
        var row = new AtomicReference<Ticket>();
        when(repo.find(id)).thenAnswer(i -> row.get());
        when(repo.lock(id)).thenAnswer(i -> row.get());
        doAnswer(i -> { row.set(i.getArgument(0)); return null; }).when(repo).reserve(any());
        doAnswer(i -> {
            Ticket old = row.get();
            row.set(new Ticket(id, old.owner(), old.name(), old.size(), old.key(), i.getArgument(1), i.getArgument(2)));
            return null;
        }).when(repo).advance(eq(id), anyString(), anyLong());
        var storage = mock(TrainingVideoStorage.class);
        String key = "eds:training-videos/key.mp4";
        when(storage.reserve("video/mp4")).thenReturn(key);
        var read = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        doAnswer(i -> {
            try (var input = i.<MultipartFile>getArgument(1).getInputStream()) {
                assertEquals(6, input.readNBytes(6).length);
                read.countDown();
                assertTrue(finish.await(5, TimeUnit.SECONDS));
                assertEquals(6, input.readAllBytes().length);
            }
            return null;
        }).when(storage).putReserved(eq(key), any());
        doThrow(new IllegalStateException("cache full")).doNothing().when(storage).warmReserved(eq(key), any());
        var service = new PreparedTrainingVideos(staging, repo, storage,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var work = executor.submit(() -> service.prepare(3, id));
            assertTrue(read.await(5, TimeUnit.SECONDS));
            var progress = executor.submit(() -> service.progress(3, id)).get(2, TimeUnit.SECONDS);
            assertEquals(new PreparedTrainingVideos.Progress(6, 12), progress);
            assertThrows(BusinessException.class, () -> service.progress(4, id));
            finish.countDown();
            assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class, work::get).getCause());
            assertEquals("STORED", row.get().phase());
            service.prepare(3, id);
            service.prepare(3, id);
            assertEquals("READY", row.get().phase());
            assertFalse(java.nio.file.Files.exists(directory.resolve(id + ".part")));
            assertEquals(new PreparedTrainingVideos.Progress(12, 12), service.progress(3, id));
            verify(storage).putReserved(eq(key), any());
            verify(storage, times(2)).warmReserved(eq(key), any());
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }
}
