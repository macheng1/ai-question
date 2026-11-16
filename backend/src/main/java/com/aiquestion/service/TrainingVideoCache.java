package com.aiquestion.service;

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
    if (limit <= 0) return;
    try {
      Files.createDirectories(this.root);
      ownership = FileChannel.open(this.root.resolve(".owner"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      directoryLock = ownership.tryLock();
      if (directoryLock == null) throw new IOException("Cache directory already in use");
      List<Path> existing = new ArrayList<>();
      try (var entries = Files.newDirectoryStream(this.root)) {
        for (Path entry : entries) {
          String name = entry.getFileName().toString();
          if (name.matches(VIDEO + "\\.part")) Files.delete(entry);
          else if (name.matches(VIDEO) && Files.isRegularFile(entry)) existing.add(entry);
        }
      }
      existing.sort(Comparator.comparingLong(p -> p.toFile().lastModified()));
      for (Path file : existing) {
        long size = Files.size(file);
        files.put(file, new Entry(size));
        used += size;
      }
      makeRoom(0);
    } catch (IOException | OverlappingFileLockException ex) {
      close();
      log.warn("视频缓存目录不可用，将从对象存储读取", ex);
    }
  }

  @PreDestroy
  public synchronized void close() {
    if (ownership != null) {
      try { ownership.close(); } catch (IOException ex) { log.warn("关闭视频缓存失败", ex); }
      ownership = null;
      files.clear();
      directoryLock = null;
      used = 0;
    }
  }

  Resource resource(String key, Supplier<StoredObject> origin) throws IOException {
    Path file = file(key);
    long length;
    synchronized (lock(file)) {
      Long hit = cachedLength(file);
      if (hit != null) length = hit;
      else {
        StoredObject object = origin.get();
        length = object.length();
        try (InputStream input = object.stream()) {
          if (length < 0) throw new IOException("对象存储未返回视频长度");
          store(file, length, input);
        }
      }
    }
    long size = length;
    return new AbstractResource() {
      @Override public String getDescription() { return key; }
      @Override public long contentLength() { return size; }
      @Override public InputStream getInputStream() throws IOException {
        InputStream cached = openCached(file);
        if (cached != null) return cached;
        // Spring's range writer expects a single skip call to reach the requested offset.
        // Network streams may make partial progress even when the full object is available.
        return new FilterInputStream(origin.get().stream()) {
          @Override public long skip(long count) throws IOException {
            if (count <= 0) return 0;
            in.skipNBytes(count);
            return count;
          }
        };
      }
    };
  }

  void warm(String key, MultipartFile upload) {
    Path file = file(key);
    synchronized (lock(file)) {
      if (cachedLength(file) != null) return;
      try (InputStream input = upload.getInputStream()) {
        store(file, upload.getSize(), input);
      } catch (IOException ex) {
        log.warn("上传完成，视频缓存预热失败", ex);
      }
    }
  }

  void delete(String key, Runnable deleteOrigin) {
    Path file = file(key);
    synchronized (lock(file)) {
      deleteOrigin.run();
      synchronized (this) {
        if (files.containsKey(file)) {
          try { remove(file); } catch (IOException ex) { log.warn("视频缓存清理失败", ex); }
        }
      }
    }
  }

  private synchronized Long cachedLength(Path file) {
    Entry entry = files.get(file);
    if (entry == null) return null;
    if (Files.isRegularFile(file)) return entry.bytes;
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

  private boolean makeRoom(long bytes) throws IOException {
    if (bytes < 0 || bytes > limit - pending || ownership == null) return false;
    while (bytes > limit - used - pending
        || bytes > Files.getFileStore(root).getUsableSpace() - reserve - pending) {
      var victim = files.entrySet().stream().filter(e -> e.getValue().readers == 0).findFirst();
      if (victim.isEmpty()) return false;
      remove(victim.get().getKey());
    }
    return true;
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

  private void store(Path file, long bytes, InputStream input) throws IOException {
    synchronized (this) {
      if (!makeRoom(bytes)) return;
      pending += bytes;
    }
    Path part = file.resolveSibling(file.getFileName() + ".part");
    boolean published = false;
    try {
      try (OutputStream output = Files.newOutputStream(part)) {
        byte[] buffer = new byte[128 * 1024];
        long total = 0;
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
    } finally {
      if (!published) {
        try { Files.deleteIfExists(part); }
        finally { synchronized (this) { pending -= bytes; } }
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
