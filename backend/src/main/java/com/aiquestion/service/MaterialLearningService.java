package com.aiquestion.service;

import com.aiquestion.common.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aiquestion.repository.MaterialLearningRepository;

@Service
public class MaterialLearningService {

    private final MaterialLearningRepository persistence;

    public MaterialLearningService(MaterialLearningRepository persistence, AssessmentRosterService assessmentRosterService, LearningClock learningClock, TrainingAudienceService trainingAudienceService) {
        this.persistence = persistence;
        this.assessmentRosterService = assessmentRosterService;
        this.learningClock = learningClock;
        this.trainingAudienceService = trainingAudienceService;
    }

    public Map<String, Object> session(long task, String token) {
        var rows = persistence.findSession(Math.abs(task), token, task > 0);
        if (rows.isEmpty())
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "学习凭证无效");
        var s = rows.get(0);
        if (task > 0)
            assessmentRosterService.requireEligible(task, s.get("candidate_name").toString(), s.get("phone").toString());
        trainingAudienceService.requireOpenTask(task, s.get("candidate_name").toString(), s.get("phone").toString());
        return s;
    }

    public List<Map<String, Object>> list(long task, long session) {
        return persistence.findMaterials(Math.abs(task), task > 0, session).stream().map(m -> {
            var pages = persistence.findPageProgress(session, com.aiquestion.repository.RepositoryRows.nullableLong(m.get("id")));
            m.put("pages", pages);
            m.put("completed", pages.stream().allMatch(p -> Boolean.TRUE.equals(p.get("completed")) || "1".equals(String.valueOf(p.get("completed")))));
            return m;
        }).toList();
    }

    public long authorize(long task, long material, String token, int page) {
        var s = session(task, token);
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countAuthorizedPage(material, Math.abs(task), page, task > 0, ((Number) s.get("id")).longValue())) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND);
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countIncompleteEarlierPages(material, page, com.aiquestion.repository.RepositoryRows.nullableLong(s.get("id")))) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先完成前面的页面");
        return ((Number) s.get("id")).longValue();
    }

    public static double creditedSeconds(double elapsed, double reported) {
        if (!Double.isFinite(reported) || reported < 0 || reported > 15)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "阅读计时无效");
        return elapsed > 20 ? 0 : Math.min(Math.max(0, elapsed), reported);
    }

    // Session locks serialize one learner; READ_COMMITTED avoids range locks blocking other learners' inserts.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> heartbeat(long task, long material, int page, String token, String previous, double seconds) {
        long session = authorize(task, material, token, page);
        // Configuration first (shared), then this learner's session/progress and clock (exclusive).
        // Material edits/deletes take the configuration lock exclusively before inspecting progress.
        var configuration = com.aiquestion.repository.RepositoryRows.one(persistence.lockMaterial(material));
        if (!"READY".equals(configuration.get("preview_status")))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "材料预览尚未生成，暂不能记录学习进度");
        com.aiquestion.repository.RepositoryRows.one(persistence.lockSession(session));
        persistence.initializePageProgress(session, material, page);
        var r = com.aiquestion.repository.RepositoryRows.one(persistence.lockPageProgress(session, material, page));
        double total = ((Number) r.get("studied_seconds")).doubleValue();
        // Retry of the last acknowledged batch: return its result without touching any clock.
        if (previous != null && previous.equals(r.get("consumed_heartbeat_token")) && r.get("heartbeat_token") != null) {
            if (!(r.get("consumed_seconds") instanceof Number n) || Double.compare(n.doubleValue(), seconds) != 0)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "重试内容与原请求不一致");
            return Map.of("studiedSeconds", total, "completed", com.aiquestion.repository.RepositoryRows.booleanValue(r,"completed"), "heartbeatToken",r.get("heartbeat_token"));
        }
        if (previous != null && previous.equals(r.get("heartbeat_token")) && r.get("elapsed") instanceof Number elapsed)
            total += learningClock.credit(session, creditedSeconds(elapsed.doubleValue(), seconds));
        else if (seconds != 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "阅读会话已更新，请重新打开当前页");
        if (seconds == 0)
            learningClock.credit(session, 0);
        boolean done = total >= ((Number) r.get("required_seconds")).intValue();
        String next = UUID.randomUUID().toString();
        // 一名学员只允许一个材料页面累计时长。
        persistence.clearHeartbeatTokens(session);
        persistence.updatePageProgress(total, done, next, previous, seconds, done, session, material, page);
        return Map.of("studiedSeconds", total, "completed", done, "heartbeatToken", next);
    }

    private final AssessmentRosterService assessmentRosterService;

    private final LearningClock learningClock;

    private final TrainingAudienceService trainingAudienceService;
}
