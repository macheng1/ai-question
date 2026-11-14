package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.storage.FileStorageService;
import java.util.*;
import java.io.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.aiquestion.repository.TrainingMaterialRepository;

@Service
public class TrainingMaterialService {

    private final ConcurrentMap<String, CompletableFuture<byte[]>> loadingPages =
        new ConcurrentHashMap<>();

    private final TrainingMaterialRepository persistence;

    private final FileStorageService oss;

    private final TrainingService trainings;

    private final com.aiquestion.repository.MaterialPreviewRepository previews;

    public TrainingMaterialService(TrainingMaterialRepository persistence, FileStorageService oss, TrainingService trainings, com.aiquestion.repository.MaterialPreviewRepository previews) {
        this.previews = previews;
        this.persistence = persistence;
        this.oss = oss;
        this.trainings = trainings;
    }

    public List<Map<String, Object>> list(long training) {
        return persistence.findMaterials(training).stream().map(r -> {
            r.put("pages", persistence.findPages(com.aiquestion.repository.RepositoryRows.nullableLong(r.get("id"))));
            return r;
        }).toList();
    }

    public Map<String, Object> material(long training, long id) {
        var rows = persistence.findMaterial(id, training);
        if (rows.isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "材料不存在");
        return rows.get(0);
    }

    @Transactional
    public long upload(long training, long user, MultipartFile file) throws IOException {
        trainings.get(training, user);
        com.aiquestion.repository.RepositoryRows.one(persistence.lockTrainingForUpload(training));
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countTrainingSessions(training)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能新增材料，请新建培训");
        var info = inspect(file);
        String title = info.title(), type = info.type(), mime = info.mime(), extension = info.extension();
        int pages = info.pageCount();
        String key = oss.uploadMaterial(file, mime, extension, "training-materials");
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) try { oss.delete(key); } catch (RuntimeException ignored) { }
            }
        });
        try {
            var inserted1 = new com.aiquestion.repository.GeneratedId();
            persistence.insertMaterial(training, title, type, key, pages, inserted1);
            Long id = inserted1.getId();
            for (int page = 1; page <= pages; page++) persistence.insertPage(id, page);
            return id;
        } catch (RuntimeException e) {
            try {
                oss.delete(key);
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
    }

    public record Inspection(String title, String type, String mime, String extension, int pageCount) {}

    public Inspection inspect(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 20 * 1024 * 1024)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "PDF和图片不能超过20MB");
        byte[] bytes = file.getBytes();
        String title = Objects.toString(file.getOriginalFilename(), "材料");
        if (title.length() > 255)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名过长");
        String suffix = title.toLowerCase(Locale.ROOT);
        String type, mime, extension;
        int pages = 1;
        if (suffix.endsWith(".pdf")) {
            try (var document = Loader.loadPDF(bytes)) {
                pages = document.getNumberOfPages();
                if (document.isEncrypted() || pages < 1 || pages > 200)
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传未加密且不超过200页的PDF");
            }
            type = "PDF";
            mime = "application/pdf";
            extension = "pdf";
        } else {
            if (!suffix.endsWith(".png") && !suffix.endsWith(".jpg") && !suffix.endsWith(".jpeg"))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持PDF、PNG、JPG");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext())
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "图片内容无效");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 40_000_000)
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "图片尺寸过大");
                    String format = reader.getFormatName();
                    if (!Set.of("png", "jpeg", "jpg").contains(format.toLowerCase(Locale.ROOT)))
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "图片内容必须为PNG或JPG");
                    extension = format.equalsIgnoreCase("png") ? "png" : "jpg";
                } finally {
                    reader.dispose();
                }
            }
            type = "IMAGE";
            mime = "png".equals(extension) ? "image/png" : "image/jpeg";
        }
        return new Inspection(title, type, mime, extension, pages);
    }

    public byte[] page(long training, long id, int page) throws IOException {
        return readPage(pageKey(training, id, page));
    }

    public ResponseEntity<?> learnerPage(long training, long id, int page, boolean direct) throws IOException {
        String key = pageKey(training, id, page);
        var response = ResponseEntity.ok().header("Cache-Control", "private, no-store");
        if (direct) {
            var url = oss.directPreviewUrl(key);
            if (url.isPresent()) return response.contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("url", url.get()));
        }
        return response.contentType(MediaType.IMAGE_PNG).body(readPage(key));
    }

    private String pageKey(long training, long id, int page) {
        var row = material(training, id);
        if (page < 1 || page > ((Number) row.get("page_count")).intValue())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "页码无效");
        if (!"READY".equals(row.get("preview_status")))
            throw new BusinessException("FAILED".equals(row.get("preview_status")) ? ErrorCode.BAD_REQUEST : ErrorCode.MATERIAL_PREVIEW_PENDING, "FAILED".equals(row.get("preview_status"))
                ? "材料预览生成失败，请联系管理员重试" : "材料预览正在生成，请稍后重试");
        String key = previews.pageKey(id, page);
        if (key == null) throw new BusinessException(ErrorCode.NOT_FOUND, "材料预览页不存在");
        return key;
    }

    private byte[] readPage(String key) throws IOException {
        var loading = new CompletableFuture<byte[]>();
        var existing = loadingPages.putIfAbsent(key, loading);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof IOException io) throw io;
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                throw e;
            }
        }
        try {
            byte[] bytes;
            try (var input = oss.open(key).stream()) {
                bytes = input.readAllBytes();
            }
            loading.complete(bytes);
            return bytes;
        } catch (IOException | RuntimeException | Error e) {
            loading.completeExceptionally(e);
            throw e;
        } finally {
            loadingPages.remove(key, loading);
        }
    }

    public void retryPreview(long training, long id, long user) {
        trainings.get(training, user);
        material(training, id);
        previews.retry(id);
    }

    @Transactional
    public void reorder(long training, long user, List<Long> ids) {
        trainings.get(training, user);
        com.aiquestion.repository.RepositoryRows.one(persistence.lockTrainingForReorder(training));
        var existing = persistence.findMaterialIds(training);
        if (ids == null || ids.size() != existing.size() || new HashSet<>(ids).size() != ids.size() || !new HashSet<>(ids).equals(new HashSet<>(existing)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "材料列表已变更，请刷新后重试");
        for (int i = 0; i < ids.size(); i++) persistence.updateSortOrder(i, com.aiquestion.repository.RepositoryRows.nullableLong(ids.get(i)), training);
    }

    public record PageTime(int pageNumber, int requiredSeconds) {
    }

    // After waiting for readers, count progress committed by those readers instead of an older snapshot.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void timings(long training, long id, long user, List<PageTime> times) {
        trainings.get(training, user);
        com.aiquestion.repository.RepositoryRows.one(persistence.lockMaterialForTiming(id, training));
        var row = material(training, id);
        if (times == null || times.size() != ((Number) row.get("page_count")).intValue() || times.stream().map(PageTime::pageNumber).distinct().count() != times.size())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请设置每一页的时长");
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countProgressForTiming(id)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能修改阅读时长");
        for (var t : times) {
            if (t.pageNumber() < 1 || t.pageNumber() > times.size() || t.requiredSeconds() < 1 || t.requiredSeconds() > 3600)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "阅读时长应为1至3600秒");
            persistence.updateRequiredSeconds(t.requiredSeconds(), id, t.pageNumber());
        }
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void delete(long training, long id, long user) {
        trainings.get(training, user);
        com.aiquestion.repository.RepositoryRows.one(persistence.lockMaterialForDelete(id, training));
        var row = material(training, id);
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countProgressForDelete(id)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能删除材料");
        if ("PROCESSING".equals(row.get("preview_status")))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "材料预览正在生成，请完成后再删除");
        var keys = new ArrayList<String>();
        keys.add(String.valueOf(row.get("storage_key")));
        for (var p : previews.pages(id)) if (p.get("preview_key") != null) keys.add(p.get("preview_key").toString());
        persistence.deleteMaterial(id);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {

            public void afterCommit() {
                for (String key : keys) try {
                    oss.delete(key);
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger(TrainingMaterialService.class).warn("材料对象清理失败 id={}", id, e);
                }
            }
        });
    }
}
