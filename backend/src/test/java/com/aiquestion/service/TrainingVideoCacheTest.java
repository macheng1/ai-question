package com.aiquestion.service;

import com.aiquestion.storage.ObjectStorage.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrainingVideoCacheTest {
  @TempDir Path directory;
  TrainingVideoCache cache;
  String key = key();
  byte[] data = {10, 20, 30, 40, 50};
  AtomicInteger reads = new AtomicInteger();

  static String key() { return "eds:training-videos/" + UUID.randomUUID() + ".mp4"; }
  Path path(String key) { return directory.resolve(key.replace(":training-videos/", "-")); }
  StoredObject origin() {
    reads.incrementAndGet();
    return new StoredObject(new ByteArrayInputStream(data) {
      @Override public synchronized long skip(long count) { return super.skip(Math.min(1, count)); }
    }, data.length, "video/mp4");
  }
  void init(long limit, long reserve) {
    if (cache != null) cache.close();
    cache = new TrainingVideoCache(directory.toString(), limit, reserve);
  }
  @AfterEach void close() { if (cache != null) cache.close(); }

  @Test void simultaneousReadersAndRestartShareOneCompleteDownload() throws Exception {
    init(100, 0);
    var pool = Executors.newFixedThreadPool(12);
    var start = new CountDownLatch(1);
    try {
      var futures = new ArrayList<Future<byte[]>>();
      for (int i = 0; i < 12; i++) futures.add(pool.submit(() -> {
        start.await();
        try (var stream = cache.resource(key, this::origin).getInputStream()) {
          return stream.readAllBytes();
        }
      }));
      start.countDown();
      for (var future : futures) assertArrayEquals(data, future.get());
    } finally { pool.shutdownNow(); }
    init(100, 0);
    try (var stream = cache.resource(key, this::origin).getInputStream()) {
      assertArrayEquals(data, stream.readAllBytes());
    }
    assertEquals(1, reads.get());
  }

  @Test void truncatedOrOverlongDownloadsAreDiscardedAndRetried() throws Exception {
    init(5, 0);
    for (int wrong : new int[]{3, 6}) {
      assertThrows(IOException.class, () -> cache.resource(key,
          () -> new StoredObject(new ByteArrayInputStream(new byte[wrong]), 5, "video/mp4")));
      assertFalse(Files.exists(path(key)));
      assertFalse(Files.exists(Path.of(path(key) + ".part")));
    }
    assertEquals(5, cache.resource(key, this::origin).contentLength());
    Files.write(Path.of(path(key()) + ".part"), data);
    init(5, 0);
    try (var paths = Files.list(directory)) {
      assertFalse(paths.anyMatch(p -> p.toString().endsWith(".part")));
    }
  }

  @Test void warmingEvictionDeletionAndProviderIsolation() throws Exception {
    init(5, 0);
    cache.warm(key, new MockMultipartFile("video", data));
    assertEquals(5, cache.resource(key, this::origin).contentLength());
    assertEquals(0, reads.get());
    String other = key.replace("eds:", "oss:");
    try (var open = cache.resource(key, this::origin).getInputStream()) {
      cache.resource(other, this::origin);
      assertTrue(Files.exists(path(key)));
      assertFalse(Files.exists(path(other)));
      assertArrayEquals(data, open.readAllBytes());
    }
    cache.resource(other, this::origin);
    assertFalse(Files.exists(path(key)));
    assertEquals(2, reads.get());
    assertThrows(IllegalStateException.class, () -> cache.delete(other, () -> { throw new IllegalStateException(); }));
    assertTrue(Files.exists(path(other)));
    cache.delete(other, () -> {});
    assertFalse(Files.exists(path(other)));
  }

  @RestController
  static class Playback {
    final TrainingVideoCache cache;
    final String key;
    final Supplier<StoredObject> origin;
    Playback(TrainingVideoCache cache, String key, Supplier<StoredObject> origin) {
      this.cache = cache;
      this.key = key;
      this.origin = origin;
    }
    @GetMapping("/video") ResponseEntity<Resource> getVideo() throws IOException {
      return ResponseEntity.ok().contentType(org.springframework.http.MediaType.valueOf("video/mp4"))
          .body(cache.resource(key, origin));
    }
  }

  @Test void nativeRangesAlsoWorkWhenDiskReservePreventsCaching() throws Exception {
    for (long reserve : new long[]{0, Long.MAX_VALUE}) {
      init(5, reserve);
      var mvc = MockMvcBuilders.standaloneSetup(new Playback(cache, key, this::origin)).build();
      mvc.perform(get("/video").header("Range", "bytes=2-3")).andExpect(status().isPartialContent())
          .andExpect(header().string("Content-Range", "bytes 2-3/5"))
          .andExpect(content().bytes(new byte[]{30, 40}));
      mvc.perform(get("/video").header("Range", "bytes=5-")).andExpect(status().isRequestedRangeNotSatisfiable());
      mvc.perform(get("/video")).andExpect(status().isOk()).andExpect(content().bytes(data));
      assertEquals(reserve == 0, Files.exists(path(key)));
    }
  }

  @Test void servesLastBytesOfTwoGiBFileWithoutReadingPrefix() throws Exception {
    long length = 2L << 30;
    try (var file = new RandomAccessFile(path(key).toFile(), "rw")) {
      file.seek(length - 2);
      file.write(new byte[]{11, 22});
    }
    init(length, 0);
    MockMvcBuilders.standaloneSetup(new Playback(cache, key, this::origin)).build()
        .perform(get("/video").header("Range", "bytes=-2"))
        .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 2147483646-2147483647/2147483648"))
        .andExpect(content().bytes(new byte[]{11, 22}));
    assertEquals(0, reads.get());
  }
}
