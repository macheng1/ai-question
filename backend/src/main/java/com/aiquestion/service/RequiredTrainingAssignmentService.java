package com.aiquestion.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aiquestion.repository.RequiredTrainingAssignmentRepository;

/**
 * 员工新增、恢复或调岗后，补充符合范围的必修培训；保留已有名单和历史记录。
 */
@Service
public class RequiredTrainingAssignmentService {

    private final RequiredTrainingAssignmentRepository persistence;

    private final TrainingEligibilityService eligibility;

    public RequiredTrainingAssignmentService(RequiredTrainingAssignmentRepository persistence, TrainingEligibilityService eligibility) {
        this.persistence = persistence;
        this.eligibility = eligibility;
    }

    public void lockAllocationOwner(long owner) {
        eligibility.lockAllocationOwner(owner);
    }

    @Transactional
    public void assignDepartment(long departmentId, long owner) {
        lockAllocationOwner(owner);
        for (Long employeeId : persistence.findActiveDepartmentEmployeeIds(departmentId, owner))
            assignEmployee(employeeId, owner);
    }

    @Transactional
    public void assignEmployee(long employeeId, long owner) {
        lockAllocationOwner(owner);
        var employees = persistence.findActiveEmployee(employeeId, owner);
        if (employees.isEmpty())
            return;
        var employee = employees.get(0);
        for (var candidate : persistence.findMatchingTrainings(owner, com.aiquestion.repository.RepositoryRows.nullableLong(employee.get("department_id")))) {
            long id = ((Number) candidate.get("id")).longValue();
            // 与名单保存串行执行，再检查范围，防止覆盖管理员正在调整的名单。
            var current = persistence.lockMatchingTraining(id, owner, com.aiquestion.repository.RepositoryRows.nullableLong(employee.get("department_id")));
            if (current.isEmpty())
                continue;
            Long exam = current.get(0).get("assessment_id") instanceof Number n ? n.longValue() : null;
            if (eligibility.excludedEmployees(exam, owner, id).contains(employeeId))
                continue;
            if (!eligibility.bind(exam, owner, id, employeeId))
                continue;
            persistence.insertAudienceIfAbsent(id, employeeId, (String) employee.get("name"), (String) employee.get("phone"), com.aiquestion.repository.RepositoryRows.nullableLong(employee.get("department_id")), (String) employee.get("department_name"), id, employeeId);
        }
    }
}
