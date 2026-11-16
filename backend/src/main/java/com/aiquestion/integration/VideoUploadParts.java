package com.aiquestion.integration;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Reuses a bounded, replayable buffer so the storage SDK can retry each part separately. */
final class VideoUploadParts {
    private static final int PART_SIZE = 8 * 1024 * 1024;

    static boolean required(String key, long size) {
        return key.startsWith("training-videos/") && size > 64L * 1024 * 1024;
    }

    interface Upload<T> {
        T send(int partNumber, InputStream input, int length);
    }

    static <T> List<T> send(InputStream source, long size, Upload<T> upload) throws IOException {
        byte[] buffer = new byte[PART_SIZE];
        List<T> tags = new ArrayList<>();
        for (long remaining = size; remaining > 0;) {
            int length = (int) Math.min(remaining, PART_SIZE);
            if (source.readNBytes(buffer, 0, length) != length) throw new EOFException("视频文件不完整");
            tags.add(upload.send(tags.size() + 1, new ByteArrayInputStream(buffer, 0, length), length));
            remaining -= length;
        }
        return tags;
    }

    private VideoUploadParts() {}
}
