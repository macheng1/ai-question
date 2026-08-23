package com.aiquestion.service;
import com.aiquestion.common.*;
import com.aiquestion.domain.AssessmentTask;
import com.aiquestion.repository.AssessmentTaskRepository;
import com.aiquestion.repository.TestPaperRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AssessmentTaskService {
    private final AssessmentTaskRepository repository;
    private final TestPaperRepository paperRepository;

    public AssessmentTaskService(AssessmentTaskRepository r, TestPaperRepository p) {
        repository = r;
        paperRepository = p;
    }

    public PageResult<AssessmentTask> page(PageQuery q, LocalDateTime startAt, LocalDateTime endAt) {
        syncExpiredStatuses();
        return PageResult.of(repository.findPage(q, startAt, endAt), repository.count(q, startAt, endAt), q);
    }

    public AssessmentTask get(Long id) {
        syncExpiredStatuses();
        var t = repository.findById(id);
        if (t == null) throw new BusinessException(ErrorCode.NOT_FOUND, "考试任务不存在");
        return t;
    }

    /** 定时同步过期考试，列表查询也会主动触发一次，避免依赖页面刷新时间。 */
    @Scheduled(fixedDelayString = "${aiquestion.assessment.status-sync-delay-ms:30000}")
    public void syncExpiredStatuses() {
        repository.markExpiredAsEnded();
    }

    public AssessmentTask create(String name, Long paperId, LocalDateTime start, LocalDateTime end, Integer duration, String scope, Boolean retake, Integer max, Boolean sq, Boolean so, Boolean show) {
        if (paperRepository.findById(paperId) == null) throw new BusinessException(ErrorCode.NOT_FOUND, "试卷不存在");
        validateTime(start, end);
        var t = new AssessmentTask();
        apply(t, name, paperId, start, end, duration, scope, retake, max, sq, so, show);
        t.setStatus(statusForTime(start, end));
        repository.insert(t);
        return get(t.getId());
    }

    public AssessmentTask update(Long id, String name, LocalDateTime start, LocalDateTime end, Integer duration, String scope, Boolean retake, Integer max, Boolean sq, Boolean so, Boolean show) {
        var t = get(id);
        validateTime(start, end);
        apply(t, name, t.getPaperId(), start, end, duration, scope, retake, max, sq, so, show);
        if (!"DISABLED".equals(t.getStatus())) {
            t.setStatus(statusForTime(start, end));
        }
        repository.update(t);
        return get(id);
    }

    private void apply(AssessmentTask t, String name, Long paperId, LocalDateTime start, LocalDateTime end, Integer duration, String scope, Boolean retake, Integer max, Boolean sq, Boolean so, Boolean show) {
        t.setName(name); t.setPaperId(paperId); t.setStartAt(start); t.setEndAt(end);
        t.setDurationMinutes(duration == null ? 30 : duration);
        t.setCandidateScope(scope == null ? "ALL" : scope);
        t.setAllowRetake(Boolean.TRUE.equals(retake)); t.setMaxAttempts(max == null ? 1 : Math.max(1, max));
        t.setShuffleQuestions(Boolean.TRUE.equals(sq)); t.setShuffleOptions(Boolean.TRUE.equals(so));
        t.setShowResultImmediately(show == null || show);
    }

    private void validateTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) throw new BusinessException(ErrorCode.BAD_REQUEST, "结束时间必须晚于开始时间");
    }

    private String statusForTime(LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(end)) return "ENDED";
        if (now.isBefore(start)) return "PUBLISHED";
        return "STARTED";
    }

    public AssessmentTask updateStatus(Long id, String status) {
        get(id);
        if (!status.matches("DRAFT|PUBLISHED|STARTED|ENDED|DISABLED")) throw new BusinessException(ErrorCode.BAD_REQUEST, "考试任务状态不合法");
        repository.updateStatus(id, status);
        return get(id);
    }

    public Map<String, Object> statistics(Long id, boolean includeRetake) { get(id); return repository.statistics(id, includeRetake); }
    public java.util.List<Map<String, Object>> records(Long id, boolean includeRetake) { get(id); return repository.records(id, includeRetake); }

    public Map<String, Object> statisticsDetail(Long id, boolean includeRetake) {
        var task = get(id); var result = new java.util.LinkedHashMap<String, Object>();
        result.put("task", task); result.put("overview", repository.statisticsOverview(id, includeRetake));
        result.put("scoreDistribution", repository.scoreDistribution(id, includeRetake));
        result.put("passDistribution", repository.passDistribution(id, includeRetake));
        result.put("submissionTrend", repository.submissionTrend(id, includeRetake));
        result.put("typeScore", repository.typeScore(id, includeRetake));
        result.put("difficultQuestions", repository.difficultQuestions(id, includeRetake));
        return result;
    }

    public void delete(Long id) {
        get(id);
        if (repository.deleteById(id) == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "考试任务不存在");
    }
}
