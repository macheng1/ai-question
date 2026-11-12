package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.security.ManagementAccess;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aiquestion.repository.OrganizationRepository;

@Service
public class OrganizationService {

    private final OrganizationRepository persistence;

    private final RequiredTrainingAssignmentService assignments;

    public OrganizationService(OrganizationRepository persistence, RequiredTrainingAssignmentService assignments) {
        this.assignments = assignments;
        this.persistence = persistence;
    }

    public List<Map<String, Object>> departments() {
        return persistence.findDepartmentsWithEmployeeCounts(ManagementAccess.userId());
    }

    public List<Map<String, Object>> employees() {
        return persistence.findEmployees(ManagementAccess.userId());
    }

    private static String optionalText(String value, int maxLength, String label) {
        if (value == null || value.isBlank())
            return null;
        String clean = value.trim();
        if (clean.length() > maxLength)
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "最多" + maxLength + "个字符");
        return clean;
    }

    static void validateStatus(String status) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status == null ? "" : status))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态不合法");
    }

    private static String name(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写有效名称");
        return name.trim();
    }

    public void requireDepartment(long id, boolean active) {
        Integer count = com.aiquestion.repository.RepositoryRows.one(persistence.countOwnedDepartments(id, ManagementAccess.userId(), active));
        if (count == null || count == 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部门不存在或不可用");
    }

    @Transactional
    public Map<String, Object> saveDepartment(Long id, String name, String status) {
        validateStatus(status);
        String clean = name(name);
        long owner = ManagementAccess.userId();
        assignments.lockAllocationOwner(owner);
        boolean restored = false;
        if (id == null) {
            var inserted1 = new com.aiquestion.repository.GeneratedId();
            persistence.insertDepartment(owner, clean, status, inserted1);
            id = inserted1.getId();
        } else {
            var previous = persistence.lockDepartmentStatus(id, owner);
            if (previous.isEmpty())
                throw new BusinessException(ErrorCode.BAD_REQUEST, "部门不存在或不可用");
            restored = "INACTIVE".equals(previous.get(0)) && "ACTIVE".equals(status);
            persistence.updateDepartment(clean, status, id, owner);
        }
        if (restored)
            assignments.assignDepartment(id, owner);
        final long saved = id;
        return departments().stream().filter(r -> ((Number) r.get("id")).longValue() == saved).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String, Object> saveEmployee(Long id, String name, String phone, String email, String jobTitle, Long departmentId, String status, String learnerType, String gender, String employeeNo, String remark) {
        validateStatus(status);
        CandidateLearningService.validate(name, phone);
        String cleanEmail = email == null || email.isBlank() ? null : email.trim();
        if (cleanEmail != null && (cleanEmail.length() > 254 || !cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写有效邮箱");
        String cleanType = learnerType == null ? "EXTERNAL" : learnerType;
        String cleanGender = gender == null ? "UNKNOWN" : gender;
        if (!Set.of("INTERNAL", "EXTERNAL").contains(cleanType))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学员类型不合法");
        if (!Set.of("UNKNOWN", "MALE", "FEMALE").contains(cleanGender))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "性别不合法");
        String cleanEmployeeNo = optionalText(employeeNo, 100, "工号");
        String cleanRemark = optionalText(remark, 1000, "备注");
        if (departmentId == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择部门");
        long owner = ManagementAccess.userId();
        assignments.lockAllocationOwner(owner);
        boolean requireActive = true;
        if (id != null) {
            var current = persistence.findEmployeeDepartment(id, owner);
            if (current.isEmpty())
                throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
            requireActive = ((Number) current.get(0).get("department_id")).longValue() != departmentId || ("ACTIVE".equals(status) && !"ACTIVE".equals(current.get(0).get("status")));
        }
        requireDepartment(departmentId, requireActive);
        if (jobTitle != null && jobTitle.length() > 100)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "岗位名称过长");
        if (id != null && com.aiquestion.repository.RepositoryRows.one(persistence.countOwnedEmployees(id, owner)) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countPhoneConflicts(phone.trim(), id == null ? 0 : id)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号已存在");
        try {
            if (id == null) {
                var inserted2 = new com.aiquestion.repository.GeneratedId();
                persistence.insertEmployee(owner, departmentId, name.trim(), phone.trim(), cleanEmail, jobTitle, status, cleanType, cleanGender, cleanEmployeeNo, cleanRemark, inserted2);
                id = inserted2.getId();
            } else
                persistence.updateEmployee(departmentId, name.trim(), phone.trim(), cleanEmail, jobTitle, status, cleanType, cleanGender, cleanEmployeeNo, cleanRemark, id, owner);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号已存在");
        }
        assignments.assignEmployee(id, owner);
        final long saved = id;
        return employees().stream().filter(r -> ((Number) r.get("id")).longValue() == saved).findFirst().orElseThrow();
    }
}
