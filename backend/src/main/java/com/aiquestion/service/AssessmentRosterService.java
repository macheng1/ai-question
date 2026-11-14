package com.aiquestion.service;

import com.aiquestion.common.*;
import java.util.*;
import org.springframework.stereotype.Service;
import com.aiquestion.repository.AssessmentRosterRepository;

@Service
public class AssessmentRosterService {

    private final AssessmentRosterRepository persistence;

    public AssessmentRosterService(AssessmentRosterRepository persistence, TrainingAudienceService trainingAudienceService) {
        this.persistence = persistence;
        this.trainingAudienceService = trainingAudienceService;
    }

    /**
     * Lock before any consistent read, so MySQL creates its snapshot after preceding writers commit.
     */
    public void lockTask(long task) {
        try {
            com.aiquestion.repository.RepositoryRows.one(persistence.lockTask(task));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
        }
    }

    /** Learner operations share configuration, while administrative changes retain exclusive locks. */
    public void shareTask(long task) {
        try {
            com.aiquestion.repository.RepositoryRows.one(persistence.shareTask(task));
            persistence.shareTrainings(task);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
        }
    }

    /** Insert/upsert holds one candidate's exclusive guard until the caller's transaction ends. */
    public void lockCandidate(long task, String name, String phone) {
        persistence.lockCandidate(task, name.trim(), phone.trim());
    }

    public boolean hasTraining(long task) {
        return com.aiquestion.repository.RepositoryRows.one(persistence.countTrainingReferences(task)) > 0;
    }

    public List<Map<String, Object>> roster(long task) {
        var rows = persistence.findTrainingRoster(task);
        for (var row : rows) {
            Object value = row.get("submitted");
            row.put("submitted", Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0);
        }
        return rows;
    }

    public void requireEligible(long task, String name, String phone) {
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countTask(task)) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
        trainingAudienceService.requireTask(task, name, phone);
    }

    private final TrainingAudienceService trainingAudienceService;
}
