package com.aiquestion.integration;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class VideoObjectLogTest {
    private final String key = "training-videos/12345678-1234-1234-1234-123456789abc.mp4";

    @Test void partialReadIsNotReportedAsFullDownload(CapturedOutput output) throws Exception {
        try (var stream = VideoObjectLog.observe("eds", key, 100, System.nanoTime(), new ByteArrayInputStream(new byte[100]))) {
            assertEquals(30, stream.skip(30));
            assertEquals(10, stream.readNBytes(10).length);
            assertEquals(0, stream.read());
        }
        assertTrue(output.getAll().contains("event=origin_close provider=eds key=" + key + " readBytes=11 skippedBytes=30 objectBytes=100"));
    }

    @Test void sdkDiagnosticsExcludePrivateRequestDetails(CapturedOutput output) {
        var error = new AmazonS3Exception("sensitive-signed-url");
        error.setErrorCode("AccessDenied");
        error.setRequestId("storage-request-123");
        error.setStatusCode(403);
        VideoObjectLog.failure("eds", "upload", key, System.nanoTime(), error);
        assertTrue(output.getAll().contains("status=403 sdkCode=AccessDenied requestId=storage-request-123"));
        assertFalse(output.getAll().contains("sensitive-signed-url"));
    }
}
