package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.aiquestion.repository.TrainingEligibilityRepository;

/**
 * 新分配资格；不用于已分配学员的学习、答题或补考鉴权。
 */
@Service
public class TrainingEligibilityService {

    private final TrainingEligibilityRepository persistence;

    public TrainingEligibilityService(TrainingEligibilityRepository persistence) {
        this.persistence = persistence;
    }

    // Acquire before any training/binding locks; serialize all allocation writers per enterprise.
    public void lockAllocationOwner(long owner) {
        persistence.lockAllocationOwner(owner);
    }

    public Set<Long> excludedEmployees(Long assessmentId, long owner) {
        return excludedEmployees(assessmentId, owner, null);
    }

    public Set<Long> excludedEmployees(Long assessmentId, long owner, Long trainingId) {
        if (trainingId != null && persistence.findOwnedTraining(trainingId, owner).isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
        if (assessmentId == null)
            return Set.of();
        var tasks = persistence.findOwnedTaskPaper(assessmentId, owner);
        if (tasks.isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "关联考试不存在");
        long paper = ((Number) tasks.get(0).get("paper_id")).longValue();
        var excluded = new HashSet<>(persistence.findSubmittedEmployees(owner, paper));
        // 原培训已绑定人员可以继续编辑；不影响原考试的答题和补考权限。
        if (trainingId != null)
            excluded.removeAll(persistence.findOriginalEmployees(owner, paper, trainingId));
        excluded.addAll(persistence.findBoundEmployees(owner, paper, trainingId));
        return excluded;
    }
    /** Must run in the same transaction as allocation. Unique-key write serializes competing trainings. */
    public boolean bind(Long assessmentId, long owner, long trainingId, long employeeId) {
        if (assessmentId == null) return true;
        var tasks = persistence.findOwnedTaskPaper(assessmentId, owner);
        if (tasks.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "关联考试不存在");
        long paper = ((Number) tasks.get(0).get("paper_id")).longValue();
        persistence.insertBinding(owner, employeeId, paper, trainingId);
        return persistence.ownsBinding(owner, employeeId, paper, trainingId).equals(java.util.List.of(1));
    }
}
