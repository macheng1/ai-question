package com.aiquestion.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

public final class VideoLog {
    private VideoLog() {}

    /** Correlate opaque credentials without writing a reusable credential into logs. */
    public static String id(String value) {
        if (value == null || value.isEmpty()) return "-";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String error(Exception ex) {
        return ex instanceof BusinessException business ? business.getErrorCode().getCode() : ex.getClass().getSimpleName();
    }

    public static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
