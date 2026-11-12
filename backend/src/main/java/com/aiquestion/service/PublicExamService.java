package com.aiquestion.service;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static com.aiquestion.controller.PublicExamController.*;
import com.aiquestion.repository.PublicExamRepository;

/**
 * 公开考试入口：加载真实试卷、保存逐题答案并完成交卷。
 */
@org.springframework.stereotype.Service()
public class PublicExamService {

    private final PublicExamRepository persistence;

    public PublicExamService(PublicExamRepository persistence, AssessmentRosterService assessmentRosterService, CandidateLearningService candidateLearningService) {
        this.persistence = persistence;
        this.assessmentRosterService = assessmentRosterService;
        this.candidateLearningService = candidateLearningService;
    }

    public ApiResponse<Map<String, Object>> get(Long id, boolean revealAnswers, Long attemptId) {
        Map<String, Object> task = task(id);
        // 是否展示答案必须由考试配置决定，不能通过请求参数绕过配置。
        boolean submitted = false;
        if (attemptId != null) {
            List<Map<String, Object>> attempts = persistence.findAttemptStatus(attemptId, id);
            if (attempts.isEmpty())
                throw new BusinessException(ErrorCode.NOT_FOUND, "答题记录不存在");
            submitted = "SUBMITTED".equals(String.valueOf(attempts.get(0).get("status")));
        }
        if (attemptId == null) {
            task.put("questions", List.of());
            task.put("showAnswers", false);
            return ApiResponse.ok(task);
        }
        if (assessmentRosterService.hasTraining(id)) {
            var identity = com.aiquestion.repository.RepositoryRows.one(persistence.findAttemptCandidate(attemptId, id));
            assessmentRosterService.requireEligible(id, String.valueOf(identity.get("candidate_name")), String.valueOf(identity.get("phone")));
        }
        boolean reviewEnabled = revealAnswers && submitted && flag(task.get("reviewEnabled"));
        List<Map<String, Object>> questions = questionRows(id, attemptId);
        questions.forEach(q -> {
            q.put("kind", kind(String.valueOf(q.get("question_type"))));
            q.put("title", q.remove("stem"));
            if (reviewEnabled) {
                q.put("answer", answerList(q.get("answer_text"), String.valueOf(q.get("question_type"))));
            } else {
                q.put("answer", List.of());
                q.put("analysis", "");
            }
            q.remove("answer_text");
            List<Map<String, Object>> options = persistence.findQuestionOptions(com.aiquestion.repository.RepositoryRows.nullableLong(q.get("id")));
            q.put("options", options);
        });
        task.put("questions", questions);
        task.put("showAnswers", reviewEnabled);
        return ApiResponse.ok(task);
    }

    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Map<String, Object>> start(Long id, CandidateCommand command) {
        assessmentRosterService.lockTask(id);
        Map<String, Object> task = task(id);
        ensureTaskOpen(task);
        com.aiquestion.service.CandidateLearningService.validate(command.name(), command.phone());
        String phone = command.phone().trim();
        String name = command.name().trim();
        assessmentRosterService.requireEligible(id, name, phone);
        List<Map<String, Object>> participants = persistence.findLatestParticipant(id, name, phone);
        long participantId;
        int attemptCount;
        if (participants.isEmpty()) {
            var inserted1 = new com.aiquestion.repository.GeneratedId();
            persistence.insertParticipant(id, name, phone, inserted1);
            participantId = inserted1.getId();
            attemptCount = 0;
        } else {
            participantId = ((Number) participants.get(0).get("id")).longValue();
            attemptCount = ((Number) participants.get(0).get("attempt_count")).intValue();
        }
        persistence.assignParticipantEmployee(participantId);
        List<Map<String, Object>> active = persistence.findActiveAttempt(id, participantId);
        long attemptId;
        Object startedAt;
        if (active.isEmpty() && attemptCount >= maxAttempts(task)) {
            Map<String, Object> latest = com.aiquestion.repository.RepositoryRows.one(persistence.findLatestSubmittedAttempt(id, participantId));
            long latestAttemptId = ((Number) latest.get("id")).longValue();
            List<Map<String, Object>> answers = persistence.findLatestAttemptAnswers(latestAttemptId);
            Map<String, Object> result = new HashMap<>(16);
            result.put("status", "SUBMITTED");
            result.put("attemptId", latestAttemptId);
            result.put("startedAt", latest.get("started_at"));
            result.put("submittedAt", latest.get("submitted_at"));
            result.put("score", latest.get("score"));
            result.put("totalScore", latest.get("total_score"));
            result.put("passScore", task.get("passScore"));
            result.put("passed", latest.get("passed"));
            boolean showAnswers = flag(task.get("reviewEnabled"));
            result.put("showAnswers", showAnswers);
            result.put("answers", showAnswers ? answers : List.of());
            int latestAttemptNo = ((Number) latest.get("attempt_no")).intValue();
            int configuredMaxAttempts = maxAttempts(task);
            result.put("attemptNo", latestAttemptNo);
            result.put("maxAttempts", configuredMaxAttempts);
            result.put("remainingAttempts", Math.max(0, configuredMaxAttempts - latestAttemptNo));
            return ApiResponse.ok(result);
        }
        if (active.isEmpty()) {
            candidateLearningService.requireComplete(id, name, phone);
            attemptCount++;
            var inserted2 = new com.aiquestion.repository.GeneratedId();
            persistence.insertAttemptFromTask(id, participantId, attemptCount, id, inserted2);
            attemptId = inserted2.getId();
            startedAt = com.aiquestion.repository.RepositoryRows.one(persistence.findAttemptStartedAt(attemptId));
            snapshotQuestions(attemptId, id, String.valueOf(task.get("paperMode")));
            persistence.updateParticipantAttemptCount(attemptCount, participantId);
        } else {
            attemptId = ((Number) active.get(0).get("id")).longValue();
            startedAt = active.get(0).get("started_at");
            snapshotQuestions(attemptId, id, String.valueOf(task.get("paperMode")));
        }
        List<Map<String, Object>> answers = persistence.findStartedAttemptAnswers(attemptId);
        Map<String, Object> result = new HashMap<>(16);
        result.put("status", "IN_PROGRESS");
        result.put("attemptId", attemptId);
        result.put("startedAt", startedAt);
        int currentAttemptNo = active.isEmpty() ? attemptCount : ((Number) active.get(0).get("attempt_no")).intValue();
        int configuredMaxAttempts = maxAttempts(task);
        result.put("attemptNo", currentAttemptNo);
        result.put("maxAttempts", configuredMaxAttempts);
        result.put("remainingAttempts", Math.max(0, configuredMaxAttempts - currentAttemptNo));
        result.put("answers", answers);
        return ApiResponse.ok(result);
    }

    public ApiResponse<Map<String, Object>> eligibility(Long id, String name, String phone) {
        Map<String, Object> task = task(id);
        com.aiquestion.service.CandidateLearningService.validate(name, phone);
        assessmentRosterService.requireEligible(id, name, phone);
        int configuredMaxAttempts = maxAttempts(task);
        List<Map<String, Object>> participants = persistence.findEligibilityParticipant(id, name.trim(), phone.trim());
        int attemptCount = participants.isEmpty() ? 0 : ((Number) participants.get(0).get("attempt_count")).intValue();
        boolean inProgress = false;
        if (!participants.isEmpty()) {
            long participantId = ((Number) participants.get(0).get("id")).longValue();
            inProgress = !persistence.findAnyActiveAttempt(id, participantId).isEmpty();
        }
        Map<String, Object> result = new HashMap<>(16);
        result.put("allowRetake", allowRetake(task));
        result.put("maxAttempts", configuredMaxAttempts);
        result.put("attemptCount", attemptCount);
        result.put("remainingAttempts", Math.max(0, configuredMaxAttempts - attemptCount));
        result.put("inProgress", inProgress);
        result.put("canStart", inProgress || attemptCount < configuredMaxAttempts);
        return ApiResponse.ok(result);
    }

    public ApiResponse<Map<String, Object>> progress(Long id, String name, String phone) {
        task(id);
        com.aiquestion.service.CandidateLearningService.validate(name, phone);
        assessmentRosterService.requireEligible(id, name, phone);
        List<Map<String, Object>> active = persistence.findCandidateActiveAttempt(id, name.trim(), phone.trim());
        if (active.isEmpty()) {
            return ApiResponse.ok(Map.of("inProgress", false));
        }
        long attemptId = ((Number) active.get(0).get("attempt_id")).longValue();
        List<Map<String, Object>> answers = persistence.findProgressAnswers(attemptId);
        return ApiResponse.ok(Map.of("inProgress", true, "attemptId", attemptId, "startedAt", active.get(0).get("started_at"), "answers", answers));
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ApiResponse<Void> answer(Long attemptId, Long questionId, AnswerCommand command) {
        ensureAttemptOpen(attemptId, false);
        Map<String, Object> question;
        try {
            question = com.aiquestion.repository.RepositoryRows.one(persistence.findQuestionForAnswer(attemptId, questionId));
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不属于本次考试");
        }
        String answer = command.answer() == null ? "" : command.answer().trim().toUpperCase(Locale.ROOT);
        String correct = String.valueOf(question.getOrDefault("answer_text", "")).trim().toUpperCase(Locale.ROOT);
        if ("TRUE_FALSE".equals(question.get("question_type")))
            answer = "A".equals(answer) ? "TRUE" : "B".equals(answer) ? "FALSE" : answer;
        boolean correctFlag = matchesAnswer(String.valueOf(question.get("question_type")), answer, correct);
        BigDecimal score = question.get("score") instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(question.get("score")));
        persistence.upsertAnswer(attemptId, questionId, answer, correct, correctFlag, correctFlag ? score : BigDecimal.ZERO);
        return ApiResponse.ok(null);
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ApiResponse<Map<String, Object>> submit(Long attemptId) {
        Map<String, Object> openAttempt = ensureAttemptOpen(attemptId, true);
        if ("SUBMITTED".equals(openAttempt.get("status"))) {
            return result(attemptId);
        }
        Map<String, Object> attempt = com.aiquestion.repository.RepositoryRows.one(persistence.findAttemptForSubmission(attemptId));
        Integer totalQuestions = com.aiquestion.repository.RepositoryRows.one(persistence.countAttemptQuestions(attemptId));
        Integer answeredQuestions = com.aiquestion.repository.RepositoryRows.one(persistence.countAnsweredQuestions(attemptId));
        if (!expired(openAttempt) && (answeredQuestions == null || totalQuestions == null || answeredQuestions < totalQuestions)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请完成所有题目后再提交");
        }
        BigDecimal score = com.aiquestion.repository.RepositoryRows.one(persistence.sumAnswerScores(attemptId));
        BigDecimal total = new BigDecimal(String.valueOf(attempt.get("total_score")));
        BigDecimal pass = new BigDecimal(String.valueOf(attempt.get("pass_score")));
        boolean passed = score.compareTo(pass) >= 0;
        persistence.submitAttempt(score, passed, attemptId);
        int attemptNo = ((Number) attempt.get("attempt_no")).intValue();
        int configuredMaxAttempts = maxAttempts(attempt);
        Map<String, Object> result = new HashMap<>(16);
        result.put("score", score);
        result.put("totalScore", total);
        result.put("passScore", pass);
        result.put("passed", passed);
        result.put("attemptNo", attemptNo);
        result.put("maxAttempts", configuredMaxAttempts);
        result.put("remainingAttempts", Math.max(0, configuredMaxAttempts - attemptNo));
        return ApiResponse.ok(result);
    }

    public ApiResponse<List<Map<String, Object>>> records(Long id, String phone) {
        return ApiResponse.ok(persistence.findCandidateRecords(id, phone));
    }

    public ApiResponse<Map<String, Object>> result(Long attemptId) {
        Map<String, Object> attempt = com.aiquestion.repository.RepositoryRows.one(persistence.findAttemptResult(attemptId));
        if (!"SUBMITTED".equals(String.valueOf(attempt.get("status")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该答题记录尚未交卷");
        }
        List<Map<String, Object>> answers = persistence.findResultAnswers(attemptId);
        int attemptNo = ((Number) attempt.get("attemptNo")).intValue();
        int configuredMaxAttempts = maxAttempts(attempt);
        attempt.remove("status");
        attempt.remove("allowRetake");
        attempt.remove("maxAttempts");
        boolean showAnswers = flag(attempt.remove("reviewEnabled"));
        attempt.put("remainingAttempts", Math.max(0, configuredMaxAttempts - attemptNo));
        attempt.put("showAnswers", showAnswers);
        attempt.put("answers", showAnswers ? answers : List.of());
        return ApiResponse.ok(attempt);
    }

    private Map<String, Object> task(Long id) {
        List<Map<String, Object>> rows = persistence.findTaskDetails(id);
        if (rows.isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
        return new HashMap<>(rows.get(0));
    }

    private List<Map<String, Object>> questionRows(Long taskId, Long attemptId) {
        return persistence.findQuestions(attemptId == null ? taskId : attemptId, attemptId == null);
    }

    private void snapshotQuestions(Long attemptId, Long taskId, String mode) {
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countSnapshotQuestions(attemptId)) > 0)
            return;
        Long paperId = com.aiquestion.repository.RepositoryRows.one(persistence.findTaskPaper(taskId));
        if (!"RANDOM".equalsIgnoreCase(mode)) {
            persistence.snapshotFixedQuestions(attemptId, paperId);
            return;
        }
        List<Map<String, Object>> configs = persistence.findRandomBankConfig(paperId);
        int sortNo = 0;
        Set<Long> selected = new HashSet<>();
        for (Map<String, Object> config : configs) {
            sortNo = snapshotType(attemptId, ((Number) config.get("bank_id")).longValue(), "SINGLE", number(config.get("single_count")), decimal(config.get("single_score")), sortNo, selected);
            sortNo = snapshotType(attemptId, ((Number) config.get("bank_id")).longValue(), "MULTIPLE", number(config.get("multiple_count")), decimal(config.get("multiple_score")), sortNo, selected);
            sortNo = snapshotType(attemptId, ((Number) config.get("bank_id")).longValue(), "TRUE_FALSE", number(config.get("true_false_count")), decimal(config.get("true_false_score")), sortNo, selected);
        }
    }

    private int snapshotType(Long attemptId, Long bankId, String type, int count, BigDecimal score, int sortNo, Set<Long> selected) {
        if (count <= 0) {
            return sortNo;
        }
        List<Long> ids = persistence.findEnabledQuestionIds(bankId, type).stream().map(rs -> com.aiquestion.repository.RepositoryRows.longValue(rs, "id")).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(ids);
        int inserted = 0;
        for (Long questionId : ids) {
            if (selected.contains(questionId)) {
                continue;
            }
            persistence.insertSnapshotQuestion(attemptId, questionId, score, sortNo++);
            selected.add(questionId);
            if (++inserted >= count) {
                break;
            }
        }
        if (inserted < count) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "随机组卷可用题目不足，请重新配置题库");
        }
        return sortNo;
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
    }

    private void ensureTaskOpen(Map<String, Object> task) {
        String status = String.valueOf(task.getOrDefault("taskStatus", "PUBLISHED"));
        if ("ENDED".equals(status) || "DISABLED".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "考试已结束，不能继续答题");
        }
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        LocalDateTime startAt = toLocalDateTime(task.get("startAt"));
        LocalDateTime endAt = toLocalDateTime(task.get("endAt"));
        if (startAt != null && now.isBefore(startAt)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "考试尚未开始");
        }
        if (endAt != null && !now.isBefore(endAt)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "考试已结束，不能继续答题");
        }
    }

    private Map<String, Object> ensureAttemptOpen(Long attemptId, boolean submitting) {
        Map<String, Object> attempt;
        try {
            var identity = com.aiquestion.repository.RepositoryRows.one(persistence.findAttemptIdentity(attemptId));
            // Use the same task-first lock order as start/retake to serialize answer writes and submission.
            assessmentRosterService.lockTask(((Number) identity.get("task_id")).longValue());
            assessmentRosterService.requireEligible(((Number) identity.get("task_id")).longValue(), String.valueOf(identity.get("candidate_name")), String.valueOf(identity.get("phone")));
            attempt = com.aiquestion.repository.RepositoryRows.one(persistence.lockAttempt(attemptId));
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "答题记录不存在");
        }
        if (submitting && "SUBMITTED".equals(attempt.get("status"))) {
            return attempt;
        }
        if (!"IN_PROGRESS".equals(String.valueOf(attempt.get("status")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该答题记录已结束，不能继续答题");
        }
        if (!submitting) {
            ensureTaskOpen(attempt);
            if (expired(attempt))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "答题时间已结束，请交卷");
        }
        return attempt;
    }

    private static boolean expired(Map<String, Object> attempt) {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        LocalDateTime end = toLocalDateTime(attempt.get("endAt"));
        LocalDateTime start = toLocalDateTime(attempt.get("startedAt"));
        return (end != null && !now.isBefore(end)) || (start != null && !now.isBefore(start.plusMinutes(number(attempt.get("duration")))));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return LocalDateTime.ofInstant(timestamp.toInstant(), java.time.ZoneOffset.UTC);
        }
        return value == null ? null : LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    private static int maxAttempts(Map<String, Object> task) {
        boolean allowRetake = allowRetake(task);
        Object configuredValue = task.get("maxAttempts");
        int configured = configuredValue instanceof Number number ? number.intValue() : 1;
        return allowRetake ? Math.max(1, configured) : 1;
    }

    private static boolean allowRetake(Map<String, Object> task) {
        Object value = task.get("allowRetake");
        return value instanceof Boolean flag ? flag : value != null && ("1".equals(String.valueOf(value)) || Boolean.parseBoolean(String.valueOf(value)));
    }

    /**
     * 将数据库中的布尔字段统一转换为 Java boolean。
     */
    private static boolean flag(Object value) {
        return value instanceof Boolean bool ? bool : value != null && ("1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value)));
    }

    private static List<String> answerList(Object value, String type) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        if (raw.isBlank())
            return List.of();
        if ("TRUE_FALSE".equals(type))
            return List.of(raw.equalsIgnoreCase("TRUE") ? "A" : "B");
        if ("MULTIPLE".equals(type) && raw.matches("[A-Za-z]+"))
            return raw.toUpperCase(Locale.ROOT).chars().mapToObj(c -> String.valueOf((char) c)).toList();
        return Arrays.stream(raw.split("[,，、\\s]+")).filter(s -> !s.isBlank()).toList();
    }

    private static String kind(String type) {
        return switch(type) {
            case "MULTIPLE" ->
                "multi";
            case "TRUE_FALSE" ->
                "judge";
            default ->
                "single";
        };
    }

    /**
     * 按题型判断答案：单选、判断题要求答案一致；多选题必须与标准答案集合完全一致，不受选项顺序影响。
     */
    private static boolean matchesAnswer(String type, String answer, String correct) {
        if ("MULTIPLE".equals(type)) {
            Set<String> selected = new HashSet<>(answerList(answer, type));
            Set<String> required = new HashSet<>(answerList(correct, type));
            return !selected.isEmpty() && !required.isEmpty() && selected.equals(required);
        }
        return normalize(answer).equals(normalize(correct));
    }

    /**
     * 统一单选、判断题答案格式。
     */
    private static String normalize(String value) {
        String normalized = value == null ? "" : value.replaceAll("[，、,\\s]+", "").toUpperCase(Locale.ROOT);
        if (normalized.matches("[A-Z]+")) {
            return normalized.chars().sorted().collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        }
        return normalized;
    }

    private final AssessmentRosterService assessmentRosterService;

    private final CandidateLearningService candidateLearningService;
}
