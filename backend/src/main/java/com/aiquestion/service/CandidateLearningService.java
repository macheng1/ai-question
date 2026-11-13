package com.aiquestion.service;

import com.aiquestion.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.aiquestion.repository.CandidateLearningRepository;

@Service
public class CandidateLearningService {

    private final CandidateLearningRepository persistence;

    public CandidateLearningService(CandidateLearningRepository persistence, AssessmentRosterService assessmentRosterService, LearningClock learningClock, MaterialLearningService materialLearningService, TrainingAudienceService trainingAudienceService) {
        this.persistence = persistence;
        this.assessmentRosterService = assessmentRosterService;
        this.learningClock = learningClock;
        this.materialLearningService = materialLearningService;
        this.trainingAudienceService = trainingAudienceService;
    }

    public static void validate(String name, String phone) {
        if (name == null || name.isBlank() || phone == null || phone.isBlank() || name.trim().length() > 100 || phone.trim().length() > 50 || !phone.trim().matches("^1[0-9]{10}$"))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写姓名和手机号");
    }

    public List<Map<String, Object>> trainings(long task) {
        return persistence.findTrainings(Math.abs(task), task > 0);
    }

    public void requireComplete(long task, String name, String phone) {
        trainingAudienceService.requireOpenTask(task, name, phone);
        Integer missing = com.aiquestion.repository.RepositoryRows.one(persistence.countIncompleteVideos(Math.abs(task), Math.abs(task), name, phone, task > 0));
        missing += com.aiquestion.repository.RepositoryRows.one(persistence.countIncompletePages(Math.abs(task), Math.abs(task), name, phone, task > 0));
        if (missing != null && missing > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先完成全部培训材料学习");
        persistence.confirmSessionsCompleted(Math.abs(task), name, phone, task > 0);
    }

    @Transactional
    public Map<String, Object> learning(long task, String name, String phone) {
        validate(name, phone);
        if (task > 0) {
            assessmentRosterService.lockTask(task);
            persistence.lockExamTrainings(task);
            assessmentRosterService.requireEligible(task, name, phone);
        } else
            com.aiquestion.repository.RepositoryRows.one(persistence.lockStandaloneTraining(-task));
        trainingAudienceService.requireOpenTask(task, name, phone);
        if (task > 0 && persistence.findTask(task).isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
        persistence.insertSessionIfAbsent(Math.abs(task), name.trim(), phone.trim(), UUID.randomUUID().toString(), task > 0);
        persistence.assignSessionEmployee(Math.abs(task), name.trim(), phone.trim(), task > 0);
        var session = com.aiquestion.repository.RepositoryRows.one(persistence.findCandidateSession(Math.abs(task), name.trim(), phone.trim(), task > 0));
        persistence.initializeVideoProgress(com.aiquestion.repository.RepositoryRows.nullableLong(session.get("id")), Math.abs(task), task > 0);
        var videos = persistence.findSessionVideos(com.aiquestion.repository.RepositoryRows.nullableLong(session.get("id")), Math.abs(task), task > 0);
        return Map.of("videos", videos, "materials", materialLearningService.list(task, ((Number) session.get("id")).longValue()), "trainings", persistence.findCandidateTrainings(Math.abs(task), task > 0, name.trim(), phone.trim()), "token", session.get("token"));
    }

    public Map<String, Object> video(long task, long video, String token) {
        var rows = persistence.findAuthorizedVideo(Math.abs(task), Math.abs(task), token, video, task > 0);
        if (rows.isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "视频或学习凭证无效");
        var identity = com.aiquestion.repository.RepositoryRows.one(persistence.findSessionIdentity(token, Math.abs(task), task > 0));
        if (task > 0)
            assessmentRosterService.requireEligible(task, String.valueOf(identity.get("candidate_name")), String.valueOf(identity.get("phone")));
        trainingAudienceService.requireOpenTask(task, String.valueOf(identity.get("candidate_name")), String.valueOf(identity.get("phone")));
        return rows.get(0);
    }

    public static void validateProgress(double position, double duration, double previous, double elapsed, boolean completed) {
        if (!Double.isFinite(position) || !Double.isFinite(duration) || position < 0 || duration <= 0 || position > duration + 1 || position > previous + elapsed + 2 || (completed && position < duration - Math.min(1, duration * 0.02)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频进度无效，请按顺序观看");
    }

    @Transactional
    public Map<String, Object> progress(long task, long video, String token, double position, double duration, boolean completed) {
        var row = video(task, video, token);
        var saved = com.aiquestion.repository.RepositoryRows.one(persistence.lockVideoProgress(com.aiquestion.repository.RepositoryRows.nullableLong(row.get("session_id")), video));
        double previous = ((Number) saved.get("position_seconds")).doubleValue();
        // The allowance belongs to the persisted baseline, never to each request.
        validateProgress(position, duration, ((Number) saved.get("baseline_position")).doubleValue(), ((Number) saved.get("elapsed")).doubleValue(), completed);
        boolean done = completed || Boolean.TRUE.equals(saved.get("completed")) || "1".equals(String.valueOf(saved.get("completed")));
        double next = Math.max(previous, position);
        double elapsed = ((Number) saved.get("recent_elapsed")).doubleValue();
        double watched = saved.get("studied_seconds") instanceof Number n ? n.doubleValue() : 0;
        double delta = saved.get("last_play_position") instanceof Number n ? position - n.doubleValue() : 0;
        double requested = elapsed <= 15 && delta > 0 && delta <= elapsed + 1 ? Math.min(delta, elapsed) : 0;
        watched += learningClock.credit(((Number) row.get("session_id")).longValue(), requested);
        persistence.updateVideoProgress(next, done, watched, position, done, com.aiquestion.repository.RepositoryRows.nullableLong(row.get("session_id")), video);
        return Map.of("positionSeconds", next, "completed", done);
    }

    private final AssessmentRosterService assessmentRosterService;

    private final LearningClock learningClock;

    private final MaterialLearningService materialLearningService;

    private final TrainingAudienceService trainingAudienceService;
}
