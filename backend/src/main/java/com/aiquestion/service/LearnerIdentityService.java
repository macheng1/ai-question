package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import org.springframework.stereotype.Service;
import com.aiquestion.repository.LearnerIdentityRepository;

/**
 * 学员身份只从所属企业的在用员工和培训名单中解析，客户端不能指定员工 ID。
 */
@Service
public class LearnerIdentityService {

    private final LearnerIdentityRepository persistence;

    private final AccountService accounts;

    public LearnerIdentityService(LearnerIdentityRepository persistence, AccountService accounts, AssessmentRosterService assessmentRosterService, TrainingAudienceService trainingAudienceService) {
        this.persistence = persistence;
        this.accounts = accounts;
        this.assessmentRosterService = assessmentRosterService;
        this.trainingAudienceService = trainingAudienceService;
    }

    public AccountService.Profile confirm(String type, long id, String name, String phone) {
        if (id <= 0 || !("TRAINING".equals(type) || "EXAM".equals(type)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "入口无效，请重新扫码进入");
        CandidateLearningService.validate(name, phone);
        boolean training = "TRAINING".equals(type);
        var owners = persistence.findEntryOwner(id, training);
        if (owners.size() != 1)
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训或考试不存在");
        var employees = persistence.findActiveEmployee(com.aiquestion.repository.RepositoryRows.nullableLong(owners.get(0)), name.trim(), phone.trim());
        if (employees.size() != 1)
            throw new BusinessException(ErrorCode.FORBIDDEN, "姓名或手机号与在用员工名单不匹配，请联系管理员");
        var profile = accounts.employee(employees.get(0));
        if (training)
            trainingAudienceService.require(id, name, phone);
        else
            assessmentRosterService.requireEligible(id, name, phone);
        return profile;
    }

    public void requireCandidate(AccountService.Profile employee, String name, String phone) {
        if (name == null || phone == null || !employee.name().equals(name.trim()) || !employee.phone().equals(phone.trim()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "学员身份已变化，请重新确认信息");
    }

    public void requireAttempt(AccountService.Profile employee, long attemptId) {
        Integer count = com.aiquestion.repository.RepositoryRows.one(persistence.countOwnedAttempt(attemptId, employee.userId(), employee.enterpriseId()));
        if (count == null || count != 1)
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该答题记录");
    }

    public AccountService.Profile learningOwner(String token) {
        if (token == null || token.isBlank())
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var owners = persistence.findSessionEmployee(token);
        if (owners.size() != 1)
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return accounts.employee(owners.get(0));
    }

    public void requireLearning(AccountService.Profile employee, String token) {
        Integer count = com.aiquestion.repository.RepositoryRows.one(persistence.countEmployeeSession(token, employee.userId()));
        if (count == null || count != 1)
            throw new BusinessException(ErrorCode.FORBIDDEN, "学习身份已失效，请重新进入培训");
    }

    private final AssessmentRosterService assessmentRosterService;

    private final TrainingAudienceService trainingAudienceService;
}
