package com.aiquestion.integration;

import java.io.EOFException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VideoUploadPartsTest {
    @TempDir Path directory;

    @Test void twoGiBFileUses256ReplayablePartsWithCorrectEndByte() throws Exception {
        long size = 2L * 1024 * 1024 * 1024;
        Path video = directory.resolve("training.mp4");
        try (var source = new RandomAccessFile(video.toFile(), "rw")) {
            source.seek(size - 1);
            source.write(42);
        }
        try (var input = Files.newInputStream(video)) {
            var parts = VideoUploadParts.send(input, size, (number, stream, length) -> {
                try {
                    assertTrue(stream.markSupported());
                    assertEquals(8 * 1024 * 1024, length);
                    stream.skipNBytes(length - 1L);
                    assertEquals(number == 256 ? 42 : 0, stream.read());
                    assertEquals(-1, stream.read());
                    return number;
                } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            });
            assertEquals(256, parts.size());
            assertEquals(256, parts.get(255));
        }
    }

    @Test void truncatedSourceDoesNotSubmitThePartialPart() {
        assertThrows(EOFException.class, () -> VideoUploadParts.send(InputStream.nullInputStream(), 12,
            (number, stream, length) -> { throw new AssertionError("Incomplete part sent"); }));
    }
}
