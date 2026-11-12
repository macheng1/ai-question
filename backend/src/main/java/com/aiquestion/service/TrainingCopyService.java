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
 * 仅复制培训配置和材料；不创建考试、不继承考试关联、名单或学习反馈记录。
 */
@Service
public class TrainingCopyService {

    private final TrainingCopyRepository persistence;

    private final TrainingService trainings;

    private final TrainingFileCopyService files;

    private final FileStorageService storage;

    public TrainingCopyService(TrainingCopyRepository persistence, TrainingService trainings, TrainingFileCopyService files, FileStorageService storage) {
        this.persistence = persistence;
        this.trainings = trainings;
        this.files = files;
        this.storage = storage;
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
