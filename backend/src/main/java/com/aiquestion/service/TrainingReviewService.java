package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.repository.*;
import com.aiquestion.security.AccountIdentity;
import java.util.*;
import org.springframework.stereotype.Service;

/** Read-only material access: never creates sessions or changes learning progress. */
@Service
public class TrainingReviewService {
    private final LearnerRecordService records;
    private final TrainingRepository trainings;
    private final TrainingMaterialRepository materials;
    private final TrainingAudienceRepository audience;

    public TrainingReviewService(LearnerRecordService records, TrainingRepository trainings,
            TrainingMaterialRepository materials, TrainingAudienceRepository audience) {
        this.records = records; this.trainings = trainings; this.materials = materials; this.audience = audience;
    }

    public void authorize(AccountIdentity identity, long id) {
        var training = records.trainings(identity).stream().filter(row -> ((Number) row.get("id")).longValue() == id)
            .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "培训不存在或未分配给你"));
        if (!Boolean.TRUE.equals(training.get("trainingEnded")) && RepositoryRows.one(audience.countNotStartedTrainings(id, false)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "培训尚未开始");
    }

    public Map<String, Object> list(AccountIdentity identity, long id) {
        authorize(identity, id);
        var videos = trainings.findReviewVideos(id);
        return Map.of("videos", videos, "materials", materials.findMaterials(id));
    }

    public Map<String, Object> video(AccountIdentity identity, long id, long video) {
        authorize(identity, id);
        var rows = trainings.findVideo(id, video);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
        return rows.get(0);
    }
}
