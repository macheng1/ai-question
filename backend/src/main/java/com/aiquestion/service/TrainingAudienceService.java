package com.aiquestion.service;

import com.aiquestion.common.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aiquestion.repository.TrainingAudienceRepository;

@Service
public class TrainingAudienceService {

    private final TrainingAudienceRepository persistence;

    public TrainingAudienceService(TrainingAudienceRepository persistence, TrainingEligibilityService trainingEligibilityService) {
        this.persistence = persistence;
        this.trainingEligibilityService = trainingEligibilityService;
    }

    public record Scope(String type, List<Long> ids) {
    }

    public Scope get(long id, long user) {
        requireOwner(id, user);
        return new Scope(com.aiquestion.repository.RepositoryRows.one(persistence.findAudienceType(id)), persistence.findScopeTargetIds(id));
    }

    private void requireOwner(long id, long user) {
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countOwnedTrainings(id, user)) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
    }

    public void lockAllocationOwner(long owner) {
        trainingEligibilityService.lockAllocationOwner(owner);
    }

    @Transactional
    public void save(long id, long user, Scope scope) {
        lockAllocationOwner(user);
        requireOwner(id, user);
        var course = com.aiquestion.repository.RepositoryRows.one(persistence.lockTraining(id));
        if (scope == null || scope.type() == null || !Set.of("ALL", "DEPARTMENT", "USERS").contains(scope.type()) || scope.ids() == null || scope.ids().stream().anyMatch(Objects::isNull))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学习范围无效");
        Set<Long> requestedIds = new TreeSet<>(scope.ids());
        if (scope.type().equals("ALL") && !requestedIds.isEmpty())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "全部员工范围无需指定部门或人员");
        var existing = get(id, user);
        boolean unchanged = Objects.equals(existing.type(), scope.type()) && requestedIds.equals(new TreeSet<>(existing.ids()));
        var previousAssignments = persistence.findAssignments(id);
        var previousIds = new HashSet<Long>();
        for (var previous : previousAssignments) previousIds.add(((Number) previous.get("employee_id")).longValue());
        boolean appendOnly = !previousAssignments.isEmpty()
            || com.aiquestion.repository.RepositoryRows.one(persistence.countLearningSessions(id)) > 0
            || (course.get("assessment_id") instanceof Number && com.aiquestion.repository.RepositoryRows.one(persistence.countExamParticipants(id)) > 0);
        if (appendOnly) {
            if (unchanged) return;
            if (course.get("end_at") instanceof java.sql.Timestamp end && !end.toLocalDateTime().isAfter(java.time.LocalDateTime.now()))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "培训已结束，不能追加学员");
            if (!Objects.equals(existing.type(), scope.type()) || !requestedIds.containsAll(existing.ids()))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "编辑时只能追加，不能移除原有部门或学员，也不能切换学习范围类型");
        }
        var employees = persistence.findActiveEmployees(user);
        Set<Long> ids = new HashSet<>(scope.ids());
        if (Set.of("DEPARTMENT", "USERS").contains(scope.type())) {
            var allowed = scope.type().equals("DEPARTMENT") ? persistence.findActiveDepartmentIds(user) : employees.stream().map(e -> ((Number) e.get("id")).longValue()).toList();
            var added = new HashSet<>(ids);
            if (appendOnly) added.removeAll(existing.ids());
            if (ids.isEmpty() || !allowed.containsAll(added))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择本企业有效的部门或员工");
        }
        var selected = employees.stream().filter(e -> scope.type().equals("ALL") || scope.type().equals("DEPARTMENT") && ids.contains(((Number) e.get("department_id")).longValue()) || scope.type().equals("USERS") && ids.contains(((Number) e.get("id")).longValue())).toList();
        if (appendOnly) selected = selected.stream().filter(e -> !previousIds.contains(((Number) e.get("id")).longValue())).toList();
        Long assessmentId = course.get("assessment_id") instanceof Number task ? task.longValue() : null;
        var excluded = trainingEligibilityService.excludedEmployees(assessmentId, user, id);
        if (scope.type().equals("USERS") && selected.stream().anyMatch(e -> excluded.contains(((Number) e.get("id")).longValue())))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选学员已绑定该试卷或已交卷，请重新选择人员");
        selected = selected.stream().filter(e -> !excluded.contains(((Number) e.get("id")).longValue()))
            .sorted(Comparator.comparingLong(e -> ((Number) e.get("id")).longValue())).toList();
        var allocated = new ArrayList<Map<String, Object>>();
        for (var employee : selected) {
            if (appendOnly && previousIds.contains(((Number) employee.get("id")).longValue())) continue;
            if (trainingEligibilityService.bind(assessmentId, user, id, ((Number) employee.get("id")).longValue()))
                allocated.add(employee);
            else if (scope.type().equals("USERS"))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "所选学员已被其他培训绑定该试卷，请刷新后重新选择");
        }
        selected = allocated;
        if (selected.isEmpty() && !appendOnly)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选择范围内没有可分配学员，请确认在用员工及该试卷的绑定情况");
        var assignedTimes = new HashMap<Long, Object>();
        for (var previous : previousAssignments) assignedTimes.put(((Number) previous.get("employee_id")).longValue(), previous.get("assigned_at"));
        if (!appendOnly) persistence.deleteAudience(id);
        persistence.deleteScopeTargets(id);
        persistence.updateAudienceType(scope.type(), id);
        for (long target : ids) persistence.insertScopeTarget(id, target);
        for (var e : selected) persistence.insertAssignment(id, ((Number) e.get("id")).longValue(), (String) e.get("name"), (String) e.get("phone"), ((Number) e.get("department_id")).longValue(), (String) e.get("department_name"), (java.sql.Timestamp) assignedTimes.get(((Number) e.get("id")).longValue()));
    }

    public void require(long training, String name, String phone) {
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countCandidateAssignments(training, name.trim(), phone.trim())) == 0)
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不在本培训的学习范围内");
    }

    public void requireOpenTask(long task, String name, String phone) {
        requireTask(task, name, phone);
        for (long id : persistence.findTrainingIdsForEntry(Math.abs(task), task > 0)) {
            if (com.aiquestion.repository.RepositoryRows.one(persistence.countEndedTrainings(id)) > 0)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "培训已结束，不能继续学习或考试");
        }
    }

    public void requireTask(long task, String name, String phone) {
        if (task < 0 && com.aiquestion.repository.RepositoryRows.one(persistence.countExamLinkedTrainings(-task)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请从关联考试进入配套培训");
        var linked = persistence.findTrainingIdsForEntry(Math.abs(task), task > 0);
        if (task < 0 && linked.isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
        var assigned = linked.stream().filter(id -> com.aiquestion.repository.RepositoryRows.one(
                persistence.countCandidateAssignments(id, name.trim(), phone.trim())) > 0).toList();
        if (!linked.isEmpty() && assigned.isEmpty())
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不在本培训的学习范围内");
        for (long id : assigned) {
            if (com.aiquestion.repository.RepositoryRows.one(persistence.countNotStartedTrainings(id, false)) > 0)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "培训尚未开始");
        }
    }

    private final TrainingEligibilityService trainingEligibilityService;
}
