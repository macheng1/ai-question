package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.domain.AssessmentTask;
import com.aiquestion.repository.*;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Training-owned exam settings; invoked inside the training save transaction. */
@Service
public class TrainingExamService {
    private final AssessmentTaskRepository tasks;
    private final TestPaperRepository papers;
    private final TrainingRepository trainings;
    private final AssessmentRosterService roster;
    private final PublicExamService publicExams;

    public TrainingExamService(AssessmentTaskRepository tasks, TestPaperRepository papers, TrainingRepository trainings, AssessmentRosterService roster, PublicExamService publicExams) {
        this.publicExams = publicExams; this.tasks = tasks; this.papers = papers; this.trainings = trainings; this.roster = roster;
    }

    public record Settings(boolean enabled, Long paperId, LocalDateTime startAt, LocalDateTime endAt,
        Integer durationMinutes, boolean allowRetake, Integer maxAttempts, boolean shuffleQuestions,
        boolean shuffleOptions, boolean showResultImmediately, String name) {
        public Settings(boolean enabled, Long paperId, LocalDateTime startAt, LocalDateTime endAt,
            Integer durationMinutes, boolean allowRetake, Integer maxAttempts, boolean shuffleQuestions,
            boolean shuffleOptions, boolean showResultImmediately) {
            this(enabled, paperId, startAt, endAt, durationMinutes, allowRetake, maxAttempts, shuffleQuestions, shuffleOptions, showResultImmediately, null);
        }
        public Settings withSchedule(LocalDateTime start, LocalDateTime end) {
            return new Settings(enabled, paperId, start.truncatedTo(java.time.temporal.ChronoUnit.MILLIS), end.truncatedTo(java.time.temporal.ChronoUnit.MILLIS), durationMinutes, allowRetake, maxAttempts, shuffleQuestions, shuffleOptions, showResultImmediately, name);
        }
    }

    public void end(long id, long user) {
        owned(id, user);
        if (RepositoryRows.one(trainings.countAssessmentReferences(id)) > 1)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该历史考试被多个培训共用，不能只结束其中一个培训的考试");
        tasks.updateStatus(id, "DISABLED");
        publicExams.finishOpenTaskAttempts(id);
    }

    public void retireIfUnlinked(Long id) { tasks.retireUnlinkedTrainingExam(id); }

    public Settings get(Long id, long owner) {
        if (id == null) return new Settings(false, null, null, null, 30, false, 1, true, true, true);
        var task = owned(id, owner);
        return settings(task);
    }

    private Settings settings(AssessmentTask t) {
        return new Settings(true, t.getPaperId(), t.getStartAt(), t.getEndAt(), t.getDurationMinutes(),
            Boolean.TRUE.equals(t.getAllowRetake()), t.getMaxAttempts(), Boolean.TRUE.equals(t.getShuffleQuestions()),
            Boolean.TRUE.equals(t.getShuffleOptions()), Boolean.TRUE.equals(t.getShowResultImmediately()), t.getName());
    }

    private AssessmentTask owned(Long id, long owner) {
        var t = tasks.findById(id);
        if (t == null || !Objects.equals(t.getCreatedBy(), owner)) throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
        return t;
    }

    public Long save(Long previous, Settings s, String trainingName, long owner, boolean locked) {
        if (!s.enabled()) return null;
        if (s.paperId() == null || s.startAt() == null || s.endAt() == null || !s.endAt().isAfter(s.startAt()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择试卷，并设置有效的考试开始和结束时间");
        if (s.durationMinutes() == null || s.durationMinutes() < 1 || s.durationMinutes() > 1440
            || (s.allowRetake() && (s.maxAttempts() == null || s.maxAttempts() < 1 || s.maxAttempts() > 100)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "考试时长须为 1–1440 分钟，答题次数须为 1–100 次");
        var paper = papers.findById(s.paperId());
        if (paper == null || !Objects.equals(paper.getCreatedBy(), owner))
            throw new BusinessException(ErrorCode.NOT_FOUND, "试卷不存在");
        AssessmentTask task = null;
        if (previous != null) {
            roster.lockTask(previous);
            task = owned(previous, owner);
            if (RepositoryRows.one(trainings.countAssessmentReferences(previous)) > 1) {
                if (settings(task).equals(s)) return previous;
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该历史考试被多个培训共用，暂不能在此修改考试设置");
            }
            if (locked && (!Objects.equals(task.getPaperId(), s.paperId()) || !Objects.equals(task.getStartAt(), s.startAt())
                || !Objects.equals(task.getDurationMinutes(), s.durationMinutes())
                || !Objects.equals(task.getShuffleQuestions(), s.shuffleQuestions()) || !Objects.equals(task.getShuffleOptions(), s.shuffleOptions())
                || !Objects.equals(task.getShowResultImmediately(), s.showResultImmediately())))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已有人开始考试，试卷、开始时间、时长和答题展示规则不能修改");
            if (locked && s.endAt().isBefore(task.getEndAt()))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "考试开始后仅可延长结束时间，提前停止请使用结束考试");
            if ((s.allowRetake() ? s.maxAttempts() : 1) < tasks.maximumUsedAttempts(previous))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "允许答题次数不能小于已经使用的次数");
            if (!Objects.equals(task.getPaperId(), s.paperId())) task = null;
        }
        boolean creating = task == null;
        if (creating && !"PUBLISHED".equals(paper.getStatus()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择已完成组卷的试卷");
        if (creating) { task = new AssessmentTask(); task.setCreatedBy(owner); task.setPaperId(s.paperId()); }
        String examName = s.name() == null ? (creating ? paper.getName() : task.getName()) : s.name().trim();
        if (examName == null || examName.isBlank() || examName.length() > 200)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写200字以内的考试名称");
        task.setName(examName); task.setStartAt(s.startAt()); task.setEndAt(s.endAt());
        task.setDurationMinutes(s.durationMinutes()); task.setCandidateScope("TRAINING");
        task.setAllowRetake(s.allowRetake()); task.setMaxAttempts(s.allowRetake() ? s.maxAttempts() : 1);
        task.setShuffleQuestions(s.shuffleQuestions()); task.setShuffleOptions(s.shuffleOptions());
        task.setShowResultImmediately(s.showResultImmediately());
        if (!"DISABLED".equals(task.getStatus())) {
            var now = com.aiquestion.common.BusinessTime.now();
            task.setStatus(!now.isBefore(s.endAt()) ? "ENDED" : now.isBefore(s.startAt()) ? "PUBLISHED" : "STARTED");
        }
        if (creating) tasks.insert(task); else tasks.update(task);
        tasks.markTrainingManaged(task.getId());
        return task.getId();
    }
}
