package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.PreparedVideoRepository;
import com.aiquestion.repository.PreparedVideoRepository.Ticket;
import com.aiquestion.storage.FileStorageService;
import com.aiquestion.storage.VideoMultipartStorage;
import com.aiquestion.storage.VideoMultipartStorage.Part;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectTrainingVideoUploadsTest {
    private static final String KEY = "training-videos/test.mp4";
    private static final String REFERENCE = "oss:" + KEY;
    private static final byte[] HEADER = {0,0,0,12,'f','t','y','p',0,0,0,0};
    private final PreparedVideoRepository tickets = mock(PreparedVideoRepository.class);
    private final VideoMultipartStorage provider = mock(VideoMultipartStorage.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final TransactionTemplate transaction = new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final DirectTrainingVideoUploads service = new DirectTrainingVideoUploads(tickets, storage, transaction, true);
    private final AtomicReference<Ticket> row = new AtomicReference<>();
    private String id;

    @BeforeEach void setup() {
        when(storage.reserveVideo("video/mp4")).thenReturn(REFERENCE);
        when(storage.videoMultipart(REFERENCE)).thenReturn(provider);
        when(provider.beginVideo(KEY, "video/mp4")).thenReturn("upload");
        doAnswer(i -> { row.set(i.getArgument(0)); return null; }).when(tickets).reserve(any());
        id = service.create(2, "demo.mp4", HEADER.length).id();
        verify(tickets).attach(id, "upload");
        when(tickets.multipartId(id)).thenReturn("upload");
        when(tickets.find(id)).thenAnswer(i -> row.get());
        when(tickets.lock(id)).thenAnswer(i -> row.get());
    }

    @Test void validatesOwnerDeclaredLengthAndIndexBeforeIssuingAnyPartAccess() throws Exception {
        var body = new ByteArrayInputStream(HEADER);
        assertThrows(BusinessException.class, () -> service.sign(1, id, 0));
        assertThrows(BusinessException.class, () -> service.sign(2, id, 1));
        assertThrows(BusinessException.class, () -> service.put(1, id, 0, 12, body));
        assertThrows(BusinessException.class, () -> service.put(2, id, 0, 13, body));
        assertThrows(BusinessException.class, () -> service.complete(1, id));
        verify(provider, never()).signVideoPart(anyString(), anyString(), anyInt());
        verify(provider, never()).putVideoPart(anyString(), anyString(), anyInt(), any(), anyLong());
        service.put(2, id, 0, 12, body);
        verify(provider).putVideoPart(eq(KEY), eq("upload"), eq(1), any(), eq(12L));
        service.sign(2, id, 0);
        verify(provider).signVideoPart(KEY, "upload", 1);
    }

    @Test void lostPartResponseMustMatchNumberSizeAndDigest() throws Exception {
        doThrow(new com.aliyun.oss.ClientException("Lost response")).when(provider)
                .putVideoPart(eq(KEY), eq("upload"), eq(1), any(), eq(12L));
        String digest = org.springframework.util.DigestUtils.md5DigestAsHex(HEADER);
        when(provider.videoParts(KEY, "upload")).thenReturn(List.of(new Part(1, 12, digest)));
        service.put(2, id, 0, 12, new ByteArrayInputStream(HEADER));
        when(provider.videoParts(KEY, "upload")).thenReturn(List.of(new Part(1, 12, "different")));
        assertThrows(com.aliyun.oss.ClientException.class,
                () -> service.put(2, id, 0, 12, new ByteArrayInputStream(HEADER)));
    }

    @Test void validatesStoredSizeAndHeaderAndCanRecoverAfterLostCompletionResponse() throws Exception {
        var parts = List.of(new Part(1, 12, "etag"));
        when(provider.videoParts(KEY, "upload")).thenReturn(parts);
        when(provider.videoLength(KEY)).thenReturn(15L, 12L, 12L);
        when(provider.videoExists(KEY)).thenReturn(false, true, true);
        when(provider.videoHeader(KEY)).thenReturn(new byte[12], HEADER);
        assertThrows(BusinessException.class, () -> service.complete(2, id));
        assertThrows(BusinessException.class, () -> service.complete(2, id));
        verify(tickets, never()).advance(anyString(), anyString(), anyLong());
        service.complete(2, id);
        verify(provider, times(1)).finishVideo(KEY, "upload", parts);
        verify(tickets).advance(eq(id), eq("READY"), anyLong());
    }

    @Test void adaptiveOssSizesFitOneListingAndEdsKeepsEightMiB() {
        int unit = 512 * 1024;
        assertEquals(unit, DirectTrainingVideoUploads.choosePartSize(REFERENCE, 12));
        for (int units = 1; units <= 4; units++) {
            long boundary = (long) unit * 1000 * units;
            assertEquals(unit * units, DirectTrainingVideoUploads.choosePartSize(REFERENCE, boundary));
            assertEquals(unit * (units + 1), DirectTrainingVideoUploads.choosePartSize(REFERENCE, boundary + 1));
        }
        assertEquals(5 * unit, DirectTrainingVideoUploads.choosePartSize(REFERENCE, 2L << 30));
        assertEquals(8 << 20, DirectTrainingVideoUploads.choosePartSize("eds:" + KEY, 12));
        assertEquals(8 << 20, DirectTrainingVideoUploads.choosePartSize("eds:" + KEY, 2L << 30));
    }

    @Test void inProgressSessionUsesItsPersistedSizeEvenOnAliyun() throws Exception {
        int eightMiB = 8 << 20;
        long size = eightMiB + 12;
        row.set(new Ticket(id, 2, "demo.mp4", size, REFERENCE, "DIRECT", Long.MAX_VALUE, eightMiB));
        assertThrows(BusinessException.class, () -> service.sign(2, id, 2));
        service.put(2, id, 1, 12, new ByteArrayInputStream(HEADER));
        verify(provider).putVideoPart(eq(KEY), eq("upload"), eq(2), any(), eq(12L));
        when(provider.videoParts(KEY, "upload")).thenReturn(List.of(new Part(1, eightMiB, "a"), new Part(2, 12, "b")));
        when(provider.videoLength(KEY)).thenReturn(size);
        when(provider.videoHeader(KEY)).thenReturn(HEADER);
        service.complete(2, id);
        verify(tickets).advance(eq(id), eq("READY"), anyLong());
    }

    @Test void rejectsWrongPartLayoutEvenWhenTotalSizeMatches() {
        int block = TrainingVideoUploads.PART_SIZE;
        long size = block + 12;
        assertThrows(BusinessException.class, () -> DirectTrainingVideoUploads.validateParts(size, block,
                List.of(new Part(1, block - 1, "a"), new Part(2, 13, "b"))));
        assertThrows(BusinessException.class, () -> DirectTrainingVideoUploads.validateParts(size, block,
                List.of(new Part(1, block, "a"), new Part(3, 12, "b"))));
        assertDoesNotThrow(() -> DirectTrainingVideoUploads.validateParts(size, block,
                List.of(new Part(1, block, "a"), new Part(2, 12, "b"))));
    }

    @Test void expiredDirectUploadIsAbortedBeforeItsTicketIsRemoved() {
        var expired = new Ticket(id, 2, "demo.mp4", 12, REFERENCE, "DIRECT", 1);
        when(tickets.lock(id)).thenReturn(expired);
        when(tickets.expired(anyLong())).thenReturn(List.of(id));
        var files = mock(TrainingVideoStorage.class);
        var prepared = new PreparedTrainingVideos(mock(TrainingVideoUploads.class), tickets, files, transaction);
        prepared.expire();
        var order = inOrder(files, tickets);
        order.verify(files).abortMultipart(REFERENCE, "upload");
        order.verify(files).delete(REFERENCE);
        order.verify(tickets).remove(id);
    }
}
