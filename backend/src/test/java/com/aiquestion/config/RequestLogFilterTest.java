package com.aiquestion.config;

import com.aiquestion.common.VideoLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class RequestLogFilterTest {
    private final RequestLogFilter filter = new RequestLogFilter("X-Trace-Id");

    @Test void rangeResponseLogsResultAndFingerprint(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/video-playback/12");
        var response = new MockHttpServletResponse();
        request.addParameter("ticket", "test-secret-playback-ticket");
        request.addHeader("Range", "bytes=1-3");
        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute("videoPlaybackSource", "ADMIN_PREVIEW");
            response.setHeader("X-Trace-Id", "range-response-trace");
            response.setHeader("Content-Range", "bytes 1-3/5");
            response.setContentLength(3);
            response.setStatus(206);
        });
        assertTrue(output.getAll().contains("source=ADMIN_PREVIEW verified=true playbackRef=" + VideoLog.id("test-secret-playback-ticket")));
        assertTrue(output.getAll().contains("range=bytes=1-3 status=206 contentRange=bytes 1-3/5 contentLength=3"));
        assertTrue(output.getAll().contains("traceId=range-response-trace"));
        assertFalse(output.getAll().contains("test-secret-playback-ticket"));
    }

    @Test void malformedRangeAndUploadIdAreRedacted(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/video-playback/12");
        request.addHeader("Range", "unexpected-sensitive-input");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        String uploadId = "764bc380-4e8f-447b-bdd1-45f0f49b3190";
        var upload = new MockHttpServletRequest("PUT", "/api/management/video-uploads/" + uploadId + "/parts/2");
        filter.doFilter(upload, new MockHttpServletResponse(), (req, res) -> {});
        assertFalse(output.getAll().contains("unexpected-sensitive-input"));
        assertFalse(output.getAll().contains(uploadId));
        assertTrue(output.getAll().contains("/video-uploads/" + VideoLog.id(uploadId) + "/parts/2"));
    }
}
