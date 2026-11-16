package com.aiquestion.integration;

import com.aiquestion.common.VideoLog;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Only application-generated training video keys are logged. Never log request URLs or SDK messages. */
final class VideoObjectLog {
    private static final Logger log = LoggerFactory.getLogger(VideoObjectLog.class);

    static boolean applies(String key) {
        return key != null && key.matches("training-videos/[a-f0-9-]{36}\\.(mp4|webm)");
    }

    static void failure(String provider, String action, String key, long start, Exception ex) {
        String code = "-", requestId = "-";
        int status = 0;
        if (ex instanceof com.amazonaws.AmazonServiceException aws) {
            code = aws.getErrorCode();
            requestId = aws.getRequestId();
            status = aws.getStatusCode();
        } else if (ex instanceof com.aliyun.oss.OSSException oss) {
            code = oss.getErrorCode();
            requestId = oss.getRequestId();
        }
        Throwable cause = ex;
        for (int depth = 0; depth < 12 && cause.getCause() != null; depth++) cause = cause.getCause();
        log.warn("video event=origin_error provider={} action={} key={} elapsedMs={} error={} cause={} status={} sdkCode={} requestId={}",
                provider, action, key, VideoLog.elapsedMs(start), ex.getClass().getSimpleName(), cause.getClass().getSimpleName(), status, code, requestId);
    }

    static InputStream observe(String provider, String key, long length, long start, InputStream source) {
        if (!applies(key)) return source;
        log.info("video event=origin_open provider={} key={} bytes={} elapsedMs={}", provider, key, length, VideoLog.elapsedMs(start));
        return new FilterInputStream(source) {
            long bytesRead, bytesSkipped;
            boolean closed;

            @Override public int read() throws IOException {
                try {
                    int value = in.read();
                    if (value >= 0) bytesRead++;
                    return value;
                } catch (IOException | RuntimeException ex) { failure(provider, "read", key, start, ex); throw ex; }
            }

            @Override public int read(byte[] data, int offset, int size) throws IOException {
                try {
                    int count = in.read(data, offset, size);
                    if (count > 0) bytesRead += count;
                    return count;
                } catch (IOException | RuntimeException ex) { failure(provider, "read", key, start, ex); throw ex; }
            }

            @Override public long skip(long count) throws IOException {
                try {
                    long skipped = in.skip(count);
                    bytesSkipped += skipped;
                    return skipped;
                } catch (IOException | RuntimeException ex) { failure(provider, "skip", key, start, ex); throw ex; }
            }

            @Override public void close() throws IOException {
                if (closed) return;
                closed = true;
                try { super.close(); }
                catch (IOException | RuntimeException ex) { failure(provider, "close", key, start, ex); throw ex; }
                finally {
                    log.info("video event=origin_close provider={} key={} readBytes={} skippedBytes={} objectBytes={} elapsedMs={}",
                            provider, key, bytesRead, bytesSkipped, length, VideoLog.elapsedMs(start));
                }
            }
        };
    }

    private VideoObjectLog() {}
}
