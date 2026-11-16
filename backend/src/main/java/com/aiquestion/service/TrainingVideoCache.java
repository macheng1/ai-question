package com.aiquestion.service;

import com.aiquestion.common.VideoLog;
import com.aiquestion.storage.ObjectStorage.StoredObject;
import jakarta.annotation.PreDestroy;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Each JVM owns one cache directory. Only complete immutable videos become visible to readers. */
@Component
public class TrainingVideoCache {
  private static final Logger log = LoggerFactory.getLogger(TrainingVideoCache.class);
  private static final String VIDEO = "(oss|eds)-[a-f0-9-]{36}\\.(mp4|webm)";
  private final Path root;
  private final long limit;
  private final long reserve;
  private final Object[] locks = new Object[128];
  private final LinkedHashMap<Path, Entry> files = new LinkedHashMap<>(16, 0.75f, true);
  private static final class Entry {
    final long bytes;
    int readers;
    boolean removed;
    Entry(long bytes) { this.bytes = bytes; }
  }
  // Includes files unlinked by a delete while an active player still owns their descriptor.
  private long used;
  private long pending;
  private FileChannel ownership;
  private FileLock directoryLock;

  public TrainingVideoCache(@Value("${training.video-cache-directory:./data/training-video-cache}") String root,
      @Value("${training.video-cache-max-bytes:8589934592}") long limit,
      @Value("${training.video-cache-reserve-bytes:2147483648}") long reserve) {
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.limit = limit;
    this.reserve = Math.max(0, reserve);
    java.util.Arrays.setAll(locks, i -> new Object());
    if (limit <= 0) {
      log.info("video event=cache_disabled reason=configured_limit limitBytes={}", limit);
      return;
    }
    String stage = "create_directory";
    try {
      Files.createDirectories(this.root);
      stage = "lock_directory";
      ownership = FileChannel.open(this.root.resolve(".owner"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      directoryLock = ownership.tryLock();
      if (directoryLock == null) throw new IOException("Cache directory already in use");
      stage = "restore_files";
      List<Path> existing = new ArrayList<>();
      try (var entries = Files.newDirectoryStream(this.root)) {
        for (Path entry : entries) {
          String name = entry.getFileName().toString();
          if (name.matches(VIDEO + "\\.part")) {
            Files.delete(entry);
            log.info("video event=cache_partial_removed cacheFile={} reason=startup", name);
          } else if (name.matches(VIDEO) && Files.isRegularFile(entry)) existing.add(entry);
        }
      }
      existing.sort(Comparator.comparingLong(p -> p.toFile().lastModified()));
      for (Path file : existing) {
        long size = Files.size(file);
        files.put(file, new Entry(size));
        used += size;
      }
      stage = "enforce_budget";
      makeRoom(0, "startup", "startup");
      log.info("video event=cache_ready files={} usedBytes={} limitBytes={} reserveBytes={}",
          files.size(), used, limit, this.reserve);
    } catch (IOException | OverlappingFileLockException ex) {
      close();
      log.warn("video event=cache_unavailable stage={} errorType={}", stage, ex.getClass().getSimpleName());
    }
  }

  @PreDestroy
  public synchronized void close() {
    if (ownership != null) {
      try { ownership.close(); } catch (IOException ex) { log.warn("video event=cache_close_failed errorType={}", ex.getClass().getSimpleName()); }
      ownership = null;
      files.clear();
      directoryLock = null;
      used = 0;
    }
  }

  Resource resource(String key, Supplier<StoredObject> origin) throws IOException {
    Path file = file(key);
    long started = System.nanoTime();
    long length;
    synchronized (lock(file)) {
      long waitMs = VideoLog.elapsedMs(started);
      Long hit = cachedLength(file);
      if (hit != null) {
        length = hit;
        log.info("video event=cache_hit key={} bytes={} lockWaitMs={}", key, length, waitMs);
      } else {
        log.info("video event=cache_miss key={} lockWaitMs={}", key, waitMs);
        StoredObject object = openOrigin(key, origin, "cache_fill");
        length = object.length();
        try (InputStream input = object.stream()) {
          if (length < 0) throw new IOException("对象存储未返回视频长度");
          store(file, length, input, key, "origin");
        }
      }
    }
    long size = length;
    return new AbstractResource() {
      @Override public String getDescription() { return key; }
      @Override public long contentLength() { return size; }
      @Override public InputStream getInputStream() throws IOException {
        InputStream cached;
        try {
          cached = openCached(file);
        } catch (IOException ex) {
          log.warn("video event=stream_open_failed key={} source=cache errorType={}", key, ex.getClass().getSimpleName());
          throw ex;
        }
        if (cached != null) {
          log.info("video event=stream_open key={} source=cache bytes={}", key, size);
          return cached;
        }
        // Spring's range writer expects a single skip call to reach the requested offset.
        // Network streams may make partial progress even when the full object is available.
        StoredObject remote = openOrigin(key, origin, "stream_fallback");
        log.info("video event=stream_open key={} source=origin bytes={}", key, remote.length());
        return new FilterInputStream(remote.stream()) {
          @Override public long skip(long count) throws IOException {
            if (count <= 0) return 0;
            in.skipNBytes(count);
            return count;
          }
        };
      }
    };
  }

  private StoredObject openOrigin(String key, Supplier<StoredObject> origin, String purpose) {
    long start = System.nanoTime();
    log.info("video event=origin_open_start key={} purpose={}", key, purpose);
    try {
      StoredObject object = origin.get();
      log.info("video event=origin_opened key={} purpose={} bytes={} elapsedMs={}", key, purpose, object.length(), VideoLog.elapsedMs(start));
      return object;
    } catch (RuntimeException ex) {
      log.warn("video event=origin_open_failed key={} purpose={} elapsedMs={} errorType={}", key, purpose, VideoLog.elapsedMs(start), ex.getClass().getSimpleName());
      throw ex;
    }
  }


  boolean warm(String key, MultipartFile upload) {
    Path file = file(key);
    long started = System.nanoTime();
    synchronized (lock(file)) {
      log.info("video event=cache_warm_start key={} bytes={} lockWaitMs={}", key, upload.getSize(), VideoLog.elapsedMs(started));
      if (cachedLength(file) != null) {
        log.info("video event=cache_warm_skipped key={} reason=already_cached", key);
        return true;
      }
      try (InputStream input = upload.getInputStream()) {
        return store(file, upload.getSize(), input, key, "upload");
      } catch (IOException ex) {
        log.warn("video event=cache_warm_failed key={} elapsedMs={} errorType={}", key, VideoLog.elapsedMs(started), ex.getClass().getSimpleName());
        return false;
      }
    }
  }

  void delete(String key, Runnable deleteOrigin) {
    Path file = file(key);
    long start = System.nanoTime();
    synchronized (lock(file)) {
      log.info("video event=origin_delete_start key={} lockWaitMs={}", key, VideoLog.elapsedMs(start));
      try {
        deleteOrigin.run();
        log.info("video event=cache_origin_deleted key={} elapsedMs={}", key, VideoLog.elapsedMs(start));
      } catch (RuntimeException ex) {
        log.warn("video event=origin_delete_failed key={} elapsedMs={} errorType={}", key, VideoLog.elapsedMs(start), ex.getClass().getSimpleName());
        throw ex;
      }
      synchronized (this) {
        if (files.containsKey(file)) {
          try {
            remove(file);
            log.info("video event=cache_deleted key={}", key);
          } catch (IOException ex) {
            log.warn("video event=cache_delete_failed key={} errorType={}", key, ex.getClass().getSimpleName());
          }
        } else log.info("video event=cache_delete_skipped key={} reason=absent", key);
      }
    }
  }

  private synchronized Long cachedLength(Path file) {
    Entry entry = files.get(file);
    if (entry == null) return null;
    if (Files.isRegularFile(file)) return entry.bytes;
    log.info("video event=cache_file_missing cacheFile={} bytes={}", file.getFileName(), entry.bytes);
    forget(file);
    return null;
  }

  private synchronized InputStream openCached(Path file) throws IOException {
    if (cachedLength(file) == null) return null;
    Entry entry = files.get(file);
    InputStream input = Files.newInputStream(file);
    entry.readers++;
    return new FilterInputStream(input) {
      private boolean closed;
      @Override public void close() throws IOException {
        synchronized (TrainingVideoCache.this) {
          if (closed) return;
          closed = true;
          try { super.close(); } finally {
            if (--entry.readers == 0 && entry.removed) used -= entry.bytes;
          }
        }
      }
    };
  }

  private boolean makeRoom(long bytes, String key, String source) throws IOException {
    String reason = null;
    if (ownership == null) reason = limit <= 0 ? "disabled" : "directory_unavailable";
    else if (bytes < 0) reason = "unknown_size";
    else if (bytes > limit - pending) reason = bytes > limit ? "object_exceeds_limit" : "concurrent_writes";
    if (reason != null) {
      log.info("video event=cache_bypass key={} source={} reason={} bytes={} limitBytes={} pendingBytes={}",
          key, source, reason, bytes, limit, pending);
      return false;
    }
    while (true) {
      boolean overBudget = bytes > limit - used - pending;
      long usable = overBudget ? -1 : Files.getFileStore(root).getUsableSpace();
      if (!overBudget && bytes <= usable - reserve - pending) return true;
      reason = overBudget ? "capacity" : "disk_reserve";
      var victim = files.entrySet().stream().filter(e -> e.getValue().readers == 0).findFirst();
      if (victim.isEmpty()) {
        log.info("video event=cache_bypass key={} source={} reason={} bytes={} usedBytes={} pendingBytes={} limitBytes={} usableBytes={} reserveBytes={} cachedFiles={}",
            key, source, reason, bytes, used, pending, limit, usable, reserve, files.size());
        return false;
      }
      var entry = victim.get();
      long victimBytes = entry.getValue().bytes;
      remove(entry.getKey());
      log.info("video event=cache_evicted cacheFile={} bytes={} reason={} requestedKey={} usedBytes={}",
          entry.getKey().getFileName(), victimBytes, reason, key, used);
    }
  }

  private void remove(Path file) throws IOException {
    Files.deleteIfExists(file);
    forget(file);
  }

  private void forget(Path file) {
    Entry removed = files.remove(file);
    removed.removed = true;
    if (removed.readers == 0) used -= removed.bytes;
  }

  private boolean store(Path file, long bytes, InputStream input, String key, String source) throws IOException {
    long started = System.nanoTime();
    synchronized (this) {
      if (!makeRoom(bytes, key, source)) return false;
      pending += bytes;
    }
    Path part = file.resolveSibling(file.getFileName() + ".part");
    boolean published = false;
    long total = 0;
    log.info("video event=cache_write_start key={} source={} bytes={}", key, source, bytes);
    try {
      try (OutputStream output = Files.newOutputStream(part)) {
        byte[] buffer = new byte[128 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
          total += count;
          if (total > bytes) throw new IOException("视频超过对象存储声明的长度");
          output.write(buffer, 0, count);
        }
        if (total != bytes) throw new IOException("视频文件长度与对象存储不符");
      }
      synchronized (this) {
        Files.move(part, file, StandardCopyOption.ATOMIC_MOVE);
        files.put(file, new Entry(bytes));
        used += bytes;
        pending -= bytes;
        published = true;
      }
      log.info("video event=cache_write_complete key={} source={} bytes={} elapsedMs={}", key, source, total, VideoLog.elapsedMs(started));
      return true;
    } catch (IOException | RuntimeException ex) {
      log.warn("video event=cache_write_failed key={} source={} receivedBytes={} expectedBytes={} elapsedMs={} errorType={}",
          key, source, total, bytes, VideoLog.elapsedMs(started), ex.getClass().getSimpleName());
      throw ex;
    } finally {
      if (!published) {
        try {
          if (Files.deleteIfExists(part)) log.info("video event=cache_partial_removed key={} reason=write_failed", key);
        } catch (IOException ex) {
          log.warn("video event=cache_partial_remove_failed key={} errorType={}", key, ex.getClass().getSimpleName());
          throw ex;
        } finally { synchronized (this) { pending -= bytes; } }
      }
    }
  }

  private Object lock(Path file) {
    return locks[Math.floorMod(file.hashCode(), locks.length)];
  }

  private Path file(String key) {
    String filename = key.replace(":training-videos/", "-");
    if (!filename.matches(VIDEO)) throw new IllegalArgumentException("Invalid video cache key");
    return root.resolve(filename);
  }
}
