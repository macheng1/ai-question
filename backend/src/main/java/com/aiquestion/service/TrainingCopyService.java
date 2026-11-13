package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.storage.FileStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.aiquestion.repository.TrainingCopyRepository;

/**
 * 复制确认后的培训配置和材料，不继承学习、答卷或反馈记录。
 */
@Service
public class TrainingCopyService {

    private final TrainingCopyRepository persistence;

    private final TrainingService trainings;

    private final TrainingFileCopyService files;

    private final FileStorageService storage;

    private final TrainingEligibilityService eligibility;
    private final TrainingEndpointService endpoints;
    private final TrainingMaterialService materials;

    public TrainingCopyService(TrainingCopyRepository persistence, TrainingService trainings, TrainingFileCopyService files, FileStorageService storage, TrainingEligibilityService eligibility, TrainingEndpointService endpoints, TrainingMaterialService materials) {
        this.persistence = persistence;
        this.trainings = trainings;
        this.files = files;
        this.storage = storage;
        this.eligibility = eligibility;
        this.endpoints = endpoints;
        this.materials = materials;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> copy(long sourceId, long userId) {
        var source = trainings.get(sourceId, userId);
        Long originalExam = source.get("assessmentId") instanceof Number id ? id.longValue() : null;
        // 与培训编辑保持相同锁顺序：先考试，后培训。
        if (originalExam != null) {
            var rows = persistence.lockOwnedAssessment(originalExam, userId);
            if (rows.isEmpty())
                throw new BusinessException(ErrorCode.NOT_FOUND, "关联考试不存在");
        }
        var locked = com.aiquestion.repository.RepositoryRows.one(persistence.lockOwnedTraining(sourceId, userId));
        Long currentExam = locked.get("assessment_id") instanceof Number id ? id.longValue() : null;
        if (!Objects.equals(originalExam, currentExam))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "培训关联考试已变更，请刷新后再复制");
        List<String> copiedObjects = new ArrayList<>();
        registerRollbackCleanup(copiedObjects);
        var inserted1 = new com.aiquestion.repository.GeneratedId();
        persistence.copyTraining(copyName(locked.get("name").toString()), sourceId, inserted1);
        long newId = inserted1.getId();
        // 空的指定人员范围使副本在重新选人前不可学习，也不会自动分配。
        persistence.resetAudience(newId);
        copyVideos(sourceId, newId, copiedObjects);
        copyMaterials(sourceId, newId, copiedObjects);
        return trainings.get(newId, userId);
    }

    public record CopyRequest(Integer sourceVersion, TrainingService.SaveTraining training) {}

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> copyAndSave(long sourceId, long userId, CopyRequest request) {
        try { return copyDraft(sourceId, userId, request, null, List.of(), null); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    public record MaterialSelection(String kind, Long sourceId, Integer uploadIndex, List<TrainingMaterialService.PageTime> pages) {}
    public record DraftRequest(CopyRequest copy, List<MaterialSelection> items) {}

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> copyDraft(long sourceId, long userId, CopyRequest request,
        List<MaterialSelection> selection, List<org.springframework.web.multipart.MultipartFile> uploads, String auth) throws java.io.IOException {
        if (request == null || request.sourceVersion() == null || request.training() == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请在复制表单确认培训配置后保存");
        eligibility.lockAllocationOwner(userId);
        var source = trainings.get(sourceId, userId);
        var tasks = new java.util.TreeSet<Long>();
        if (source.get("assessmentId") instanceof Number exam) tasks.add(exam.longValue());
        if (request.training().assessmentId() != null) tasks.add(request.training().assessmentId());
        for (long exam : tasks) {
            if (persistence.lockOwnedAssessment(exam, userId).isEmpty())
                throw new BusinessException(ErrorCode.BAD_REQUEST, "只能关联自己创建的考试");
        }
        var locked = com.aiquestion.repository.RepositoryRows.one(persistence.lockOwnedTraining(sourceId, userId));
        if (((Number) locked.get("version")).intValue() != request.sourceVersion())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原培训已修改，请刷新复制表单后重试");
        // 表单校验、分配名单和文件复制必须同时成功，不能留下半成品副本。
        var created = trainings.save(null, request.training(), userId);
        long newId = ((Number) created.get("id")).longValue();
        List<String> copiedObjects = new ArrayList<>();
        registerRollbackCleanup(copiedObjects);
        if (selection == null) {
            copyVideos(sourceId, newId, copiedObjects);
            copyMaterials(sourceId, newId, copiedObjects);
        } else {
            copySelected(sourceId, newId, userId, selection, uploads, auth, copiedObjects);
        }
        return trainings.get(newId, userId);
    }

    private void copySelected(long sourceId, long newId, long userId, List<MaterialSelection> selection,
        List<org.springframework.web.multipart.MultipartFile> uploads, String auth, List<String> keys) throws java.io.IOException {
        var used = new java.util.HashSet<String>();
        var uploadIds = new java.util.HashSet<Integer>();
        var videoIds = new ArrayList<Long>();
        var materialIds = new ArrayList<Long>();
        for (var item : selection) {
            if (item == null || !("videos".equals(item.kind()) || "materials".equals(item.kind()))
                || (item.sourceId() == null) == (item.uploadIndex() == null))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "材料配置不正确");
            boolean video = "videos".equals(item.kind());
            long addedId;
            if (item.sourceId() != null) {
                if (!used.add(item.kind() + item.sourceId())) throw new BusinessException(ErrorCode.BAD_REQUEST, "材料重复");
                var source = (video ? persistence.findSourceVideos(sourceId) : persistence.findSourceMaterials(sourceId)).stream()
                    .filter(row -> ((Number) row.get("id")).longValue() == item.sourceId()).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "原培训材料已变更，请刷新后重试"));
                String key = video ? files.copyVideo(source) : files.copyMaterial(source);
                keys.add(key);
                if (video) {
                    persistence.insertVideo(newId, (String) source.get("title"), key, (String) source.get("content_type"), 0);
                    addedId = persistence.findSourceVideos(newId).stream().mapToLong(row -> ((Number) row.get("id")).longValue()).max().orElseThrow();
                } else {
                    var generated = new com.aiquestion.repository.GeneratedId();
                    persistence.insertMaterial(newId, (String) source.get("title"), (String) source.get("material_type"), key, ((Number) source.get("page_count")).intValue(), 0, generated);
                    addedId = generated.getId();
                    persistence.copyMaterialPages(addedId, item.sourceId());
                }
            } else {
                int index = item.uploadIndex();
                if (index < 0 || index >= uploads.size() || !uploadIds.add(index)) throw new BusinessException(ErrorCode.BAD_REQUEST, "上传材料不匹配");
                if (video) {
                    endpoints.upload(newId, uploads.get(index), auth);
                    addedId = persistence.findSourceVideos(newId).stream().mapToLong(row -> ((Number) row.get("id")).longValue()).max().orElseThrow();
                } else addedId = materials.upload(newId, userId, uploads.get(index));
            }
            if (video) videoIds.add(addedId);
            else {
                if (item.pages() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "请设置材料阅读时长");
                materials.timings(newId, addedId, userId, item.pages());
                materialIds.add(addedId);
            }
        }
        if (uploadIds.size() != uploads.size()) throw new BusinessException(ErrorCode.BAD_REQUEST, "上传材料不匹配");
        trainings.reorderVideos(newId, userId, videoIds);
        materials.reorder(newId, userId, materialIds);
    }

    private void copyVideos(long sourceId, long newId, List<String> keys) {
        for (var video : persistence.findSourceVideos(sourceId)) {
            String key = files.copyVideo(video);
            keys.add(key);
            persistence.insertVideo(newId, (String) video.get("title"), key, (String) video.get("content_type"), ((Number) video.get("sort_order")).intValue());
        }
    }

    private void copyMaterials(long sourceId, long newId, List<String> keys) {
        for (var material : persistence.findSourceMaterials(sourceId)) {
            String key = files.copyMaterial(material);
            keys.add(key);
            var inserted3 = new com.aiquestion.repository.GeneratedId();
            persistence.insertMaterial(newId, (String) material.get("title"), (String) material.get("material_type"), key, ((Number) material.get("page_count")).intValue(), ((Number) material.get("sort_order")).intValue(), inserted3);
            long materialId = inserted3.getId();
            persistence.copyMaterialPages(materialId, ((Number) material.get("id")).longValue());
        }
    }

    private void registerRollbackCleanup(List<String> keys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED)
                    return;
                for (String key : keys) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException ex) {
                        LoggerFactory.getLogger(TrainingCopyService.class).warn("培训复制回滚后清理对象失败: {}", key, ex);
                    }
                }
            }
        });
    }

    private static String copyName(String name) {
        return name.substring(0, Math.min(name.length(), 156)) + "（副本）";
    }
}
