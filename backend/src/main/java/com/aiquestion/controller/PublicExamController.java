package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 公开考试入口：加载真实试卷、保存逐题答案并完成交卷。 */
@Tag(name = "公开考试", description = "考试详情、开始答题、逐题保存和交卷")
@RestController
@RequestMapping("/public/exams")
public class PublicExamController {
    private final JdbcTemplate jdbc;
    public PublicExamController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Operation(summary = "查询考试详情和题目")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id,
                                                @RequestParam(defaultValue = "false") boolean revealAnswers,
                                                @RequestParam(required = false) Long attemptId) {
        Map<String, Object> task = task(id);
        // 是否展示答案必须由考试配置决定，不能通过请求参数绕过配置。
        boolean reviewEnabled = flag(task.get("reviewEnabled"));
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
            List<Map<String, Object>> options = jdbc.queryForList("SELECT option_key `key`,option_text text FROM question_option WHERE question_id=? ORDER BY sort_no,id", q.get("id"));
            q.put("options", options);
        });
        task.put("questions", questions);
        task.put("showAnswers", reviewEnabled);
        return ApiResponse.ok(task);
    }

    @Operation(summary = "开始或恢复考试")
    @PostMapping("/{id}/start")
    public ApiResponse<Map<String, Object>> start(@PathVariable Long id, @RequestBody CandidateCommand command) {
        Map<String, Object> task = task(id);
        ensureTaskOpen(task);
        String phone = command.phone() == null ? "" : command.phone().trim();
        String name = command.name().trim();
        List<Map<String, Object>> participants = jdbc.queryForList("SELECT id,attempt_count FROM assessment_participant WHERE task_id=? AND candidate_name=? AND phone=? ORDER BY id DESC LIMIT 1", id, name, phone);
        long participantId;
        int attemptCount;
        if (participants.isEmpty()) {
            jdbc.update("INSERT INTO assessment_participant(task_id,candidate_name,phone,status,attempt_count,created_at,updated_at) VALUES(?,?,?,'IN_PROGRESS',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", id, name, phone);
            participantId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            attemptCount = 0;
        } else { participantId = ((Number) participants.get(0).get("id")).longValue(); attemptCount = ((Number) participants.get(0).get("attempt_count")).intValue(); }
        List<Map<String, Object>> active = jdbc.queryForList("SELECT id,attempt_no,started_at FROM assessment_attempt WHERE task_id=? AND participant_id=? AND status='IN_PROGRESS' ORDER BY id DESC LIMIT 1", id, participantId);
        long attemptId;
        Object startedAt;
        if (active.isEmpty() && attemptCount >= maxAttempts(task)) {
            Map<String, Object> latest = jdbc.queryForMap(
                    "SELECT id,attempt_no,started_at,submitted_at,score,total_score,passed FROM assessment_attempt WHERE task_id=? AND participant_id=? AND status='SUBMITTED' ORDER BY attempt_no DESC LIMIT 1",
                    id, participantId);
            long latestAttemptId = ((Number) latest.get("id")).longValue();
            List<Map<String, Object>> answers = jdbc.queryForList("SELECT question_id,answer_text FROM assessment_answer WHERE attempt_id=?", latestAttemptId);
            Map<String, Object> result = new HashMap<>();
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
            attemptCount++;
            jdbc.update("INSERT INTO assessment_attempt(task_id,participant_id,attempt_no,status,total_score,started_at,created_at,updated_at) SELECT ?,?,?, 'IN_PROGRESS',p.total_score,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3) FROM test_paper p JOIN assessment_task a ON a.paper_id=p.id WHERE a.id=?", id, participantId, attemptCount, id);
            attemptId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            startedAt = jdbc.queryForObject("SELECT started_at FROM assessment_attempt WHERE id=?", Object.class, attemptId);
            snapshotQuestions(attemptId, id, String.valueOf(task.get("paperMode")));
            jdbc.update("UPDATE assessment_participant SET attempt_count=?,status='IN_PROGRESS',updated_at=CURRENT_TIMESTAMP(3) WHERE id=?", attemptCount, participantId);
        } else { attemptId = ((Number) active.get(0).get("id")).longValue(); startedAt = active.get(0).get("started_at"); snapshotQuestions(attemptId, id, String.valueOf(task.get("paperMode"))); }
        List<Map<String, Object>> answers = jdbc.queryForList("SELECT question_id,answer_text FROM assessment_answer WHERE attempt_id=?", attemptId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "IN_PROGRESS");
        result.put("attemptId", attemptId);
        result.put("startedAt", startedAt);
        int currentAttemptNo = active.isEmpty()
                ? attemptCount
                : ((Number) active.get(0).get("attempt_no")).intValue();
        int configuredMaxAttempts = maxAttempts(task);
        result.put("attemptNo", currentAttemptNo);
        result.put("maxAttempts", configuredMaxAttempts);
        result.put("remainingAttempts", Math.max(0, configuredMaxAttempts - currentAttemptNo));
        result.put("answers", answers);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "检查考生是否可以开始或重做考试")
    @GetMapping("/{id}/eligibility")
    public ApiResponse<Map<String, Object>> eligibility(@PathVariable Long id,
                                                         @RequestParam String name,
                                                         @RequestParam String phone) {
        Map<String, Object> task = task(id);
        int configuredMaxAttempts = maxAttempts(task);
        List<Map<String, Object>> participants = jdbc.queryForList(
                "SELECT id,attempt_count FROM assessment_participant WHERE task_id=? AND candidate_name=? AND phone=? ORDER BY id DESC LIMIT 1",
                id, name.trim(), phone.trim());
        int attemptCount = participants.isEmpty()
                ? 0
                : ((Number) participants.get(0).get("attempt_count")).intValue();
        boolean inProgress = false;
        if (!participants.isEmpty()) {
            long participantId = ((Number) participants.get(0).get("id")).longValue();
            inProgress = !jdbc.queryForList(
                    "SELECT id FROM assessment_attempt WHERE task_id=? AND participant_id=? AND status='IN_PROGRESS' LIMIT 1",
                    id, participantId).isEmpty();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("allowRetake", allowRetake(task));
        result.put("maxAttempts", configuredMaxAttempts);
        result.put("attemptCount", attemptCount);
        result.put("remainingAttempts", Math.max(0, configuredMaxAttempts - attemptCount));
        result.put("inProgress", inProgress);
        result.put("canStart", inProgress || attemptCount < configuredMaxAttempts);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "查询考生进行中的考试")
    @GetMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> progress(@PathVariable Long id,
                                                      @RequestParam String name,
                                                      @RequestParam String phone) {
        task(id);
        List<Map<String, Object>> active = jdbc.queryForList(
                        "SELECT aa.id attempt_id,aa.started_at " +
                        "FROM assessment_attempt aa " +
                        "JOIN assessment_participant ap ON ap.id=aa.participant_id " +
                        "WHERE aa.task_id=? AND ap.candidate_name=? AND ap.phone=? " +
                        "AND aa.status='IN_PROGRESS' ORDER BY aa.id DESC LIMIT 1",
                id, name.trim(), phone.trim());
        if (active.isEmpty()) return ApiResponse.ok(Map.of("inProgress", false));
        long attemptId = ((Number) active.get(0).get("attempt_id")).longValue();
        List<Map<String, Object>> answers = jdbc.queryForList(
                "SELECT question_id,answer_text FROM assessment_answer WHERE attempt_id=?",
                attemptId);
        return ApiResponse.ok(Map.of("inProgress", true, "attemptId", attemptId, "startedAt", active.get(0).get("started_at"), "answers", answers));
    }

    @Operation(summary = "保存单题答案")
    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    public ApiResponse<Void> answer(@PathVariable Long attemptId, @PathVariable Long questionId, @RequestBody AnswerCommand command) {
        ensureAttemptOpen(attemptId);
        Map<String, Object> question = jdbc.queryForMap("SELECT q.question_type,q.answer_text,aq.score FROM assessment_attempt_question aq JOIN question_item q ON q.id=aq.question_id WHERE aq.attempt_id=? AND aq.question_id=?", attemptId, questionId);
        String answer = command.answer() == null ? "" : command.answer().trim().toUpperCase(Locale.ROOT);
        String correct = String.valueOf(question.getOrDefault("answer_text", "")).trim().toUpperCase(Locale.ROOT);
        if ("TRUE_FALSE".equals(question.get("question_type"))) answer = "A".equals(answer) ? "TRUE" : "B".equals(answer) ? "FALSE" : answer;
        boolean correctFlag = matchesAnswer(String.valueOf(question.get("question_type")), answer, correct);
        BigDecimal score = question.get("score") instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(question.get("score")));
        jdbc.update("INSERT INTO assessment_answer(attempt_id,question_id,answer_text,correct_answer,is_correct,score,answered_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE answer_text=VALUES(answer_text),correct_answer=VALUES(correct_answer),is_correct=VALUES(is_correct),score=VALUES(score),answered_at=CURRENT_TIMESTAMP(3)", attemptId, questionId, answer, correct, correctFlag, correctFlag ? score : BigDecimal.ZERO);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "交卷并计算成绩")
    @PostMapping("/attempts/{attemptId}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long attemptId) {
        ensureAttemptOpen(attemptId);
        Map<String, Object> attempt = jdbc.queryForMap("SELECT a.task_id,a.participant_id,a.attempt_no,t.paper_id,t.allow_retake allowRetake,t.max_attempts maxAttempts,p.total_score,p.pass_score FROM assessment_attempt a JOIN assessment_task t ON t.id=a.task_id JOIN test_paper p ON p.id=t.paper_id WHERE a.id=?", attemptId);
        Integer totalQuestions = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attempt_question WHERE attempt_id=?", Integer.class, attemptId);
        Integer answeredQuestions = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT question_id) FROM assessment_answer WHERE attempt_id=? AND answer_text IS NOT NULL AND TRIM(answer_text)<>''",
                Integer.class, attemptId);
        if (answeredQuestions == null || totalQuestions == null || answeredQuestions < totalQuestions) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请完成所有题目后再提交");
        }
        BigDecimal score = jdbc.queryForObject("SELECT COALESCE(SUM(score),0) FROM assessment_answer WHERE attempt_id=?", BigDecimal.class, attemptId);
        BigDecimal total = new BigDecimal(String.valueOf(attempt.get("total_score"))); BigDecimal pass = new BigDecimal(String.valueOf(attempt.get("pass_score")));
        boolean passed = score.compareTo(pass) >= 0;
        jdbc.update("UPDATE assessment_attempt SET status='SUBMITTED',score=?,passed=?,submitted_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=?", score, passed, attemptId);
        int attemptNo = ((Number) attempt.get("attempt_no")).intValue();
        int configuredMaxAttempts = maxAttempts(attempt);
        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        result.put("totalScore", total);
        result.put("passScore", pass);
        result.put("passed", passed);
        result.put("attemptNo", attemptNo);
        result.put("maxAttempts", configuredMaxAttempts);
        result.put("remainingAttempts", Math.max(0, configuredMaxAttempts - attemptNo));
        return ApiResponse.ok(result);
    }

    @Operation(summary = "按手机号查询成绩")
    @GetMapping("/{id}/records")
    public ApiResponse<List<Map<String, Object>>> records(@PathVariable Long id, @RequestParam String phone) {
        return ApiResponse.ok(jdbc.queryForList("SELECT t.name title,a.score,a.total_score totalScore,a.passed,a.submitted_at submittedAt FROM assessment_attempt a JOIN assessment_participant p ON p.id=a.participant_id JOIN assessment_task t ON t.id=a.task_id WHERE a.task_id=? AND p.phone=? AND a.status='SUBMITTED' ORDER BY a.id DESC", id, phone));
    }

    @Operation(summary = "查询单次考试成绩详情")
    @GetMapping("/attempts/{attemptId}/result")
    public ApiResponse<Map<String, Object>> result(@PathVariable Long attemptId) {
        Map<String, Object> attempt = jdbc.queryForMap(
                "SELECT a.id attemptId,a.task_id taskId,a.attempt_no attemptNo,a.status,a.score,a.total_score totalScore,p.pass_score passScore,a.passed,a.started_at startedAt,a.submitted_at submittedAt,ap.candidate_name candidateName,ap.phone,t.allow_retake allowRetake,t.max_attempts maxAttempts,t.show_result_immediately reviewEnabled "
                        + "FROM assessment_attempt a JOIN assessment_task t ON t.id=a.task_id JOIN test_paper p ON p.id=t.paper_id JOIN assessment_participant ap ON ap.id=a.participant_id WHERE a.id=?",
                attemptId);
        if (!"SUBMITTED".equals(String.valueOf(attempt.get("status")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该答题记录尚未交卷");
        }
        List<Map<String, Object>> answers = jdbc.queryForList(
                "SELECT question_id questionId,answer_text answerText,is_correct isCorrect,score FROM assessment_answer WHERE attempt_id=? ORDER BY question_id",
                attemptId);
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

    private Map<String, Object> task(Long id) { List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.id,a.name title,p.name subtitle,p.id paperId,p.mode paperMode,CASE WHEN p.mode='RANDOM' THEN COALESCE((SELECT SUM(r.single_count+r.multiple_count+r.true_false_count) FROM test_paper_random_bank r WHERE r.paper_id=p.id),0) ELSE (SELECT COUNT(*) FROM test_paper_question pq WHERE pq.paper_id=p.id) END questionCount,a.duration_minutes duration,p.total_score totalScore,p.pass_score passScore,a.allow_retake allowRetake,a.max_attempts maxAttempts,a.start_at startAt,a.end_at endAt,CASE WHEN a.end_at<=CURRENT_TIMESTAMP(3) THEN 'ENDED' ELSE a.status END taskStatus,a.show_result_immediately reviewEnabled FROM assessment_task a JOIN test_paper p ON p.id=a.paper_id WHERE a.id=?", id); if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在"); return new HashMap<>(rows.get(0)); }
    private List<Map<String,Object>> questionRows(Long taskId, Long attemptId) {
        String sql = attemptId == null
                ? "SELECT q.id,q.question_type,q.stem,q.answer_text,q.analysis,pq.score FROM assessment_task a JOIN test_paper_question pq ON pq.paper_id=a.paper_id JOIN question_item q ON q.id=pq.question_id WHERE a.id=? ORDER BY pq.sort_no,pq.id"
                : "SELECT q.id,q.question_type,q.stem,q.answer_text,q.analysis,aq.score FROM assessment_attempt_question aq JOIN question_item q ON q.id=aq.question_id WHERE aq.attempt_id=? ORDER BY aq.sort_no,aq.id";
        return jdbc.queryForList(sql, attemptId == null ? taskId : attemptId);
    }
    private void snapshotQuestions(Long attemptId, Long taskId, String mode) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attempt_question WHERE attempt_id=?", Integer.class, attemptId) > 0) return;
        Long paperId = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, taskId);
        if (!"RANDOM".equalsIgnoreCase(mode)) {
            jdbc.update("INSERT INTO assessment_attempt_question(attempt_id,question_id,score,sort_no) SELECT ?,question_id,score,sort_no FROM test_paper_question WHERE paper_id=? ORDER BY sort_no,id", attemptId, paperId);
            return;
        }
        List<Map<String,Object>> configs = jdbc.queryForList("SELECT bank_id,single_count,single_score,multiple_count,multiple_score,true_false_count,true_false_score FROM test_paper_random_bank WHERE paper_id=? ORDER BY id", paperId);
        int sortNo = 0;
        Set<Long> selected = new HashSet<>();
        for (Map<String,Object> config : configs) {
            sortNo = snapshotType(attemptId, ((Number) config.get("bank_id")).longValue(), "SINGLE", number(config.get("single_count")), decimal(config.get("single_score")), sortNo, selected);
            sortNo = snapshotType(attemptId, ((Number) config.get("bank_id")).longValue(), "MULTIPLE", number(config.get("multiple_count")), decimal(config.get("multiple_score")), sortNo, selected);
            sortNo = snapshotType(attemptId, ((Number) config.get("bank_id")).longValue(), "TRUE_FALSE", number(config.get("true_false_count")), decimal(config.get("true_false_score")), sortNo, selected);
        }
    }
    private int snapshotType(Long attemptId, Long bankId, String type, int count, BigDecimal score, int sortNo, Set<Long> selected) {
        if (count <= 0) return sortNo;
        List<Long> ids = jdbc.query("SELECT id FROM question_item WHERE bank_id=? AND question_type=? AND status='ENABLED'", (rs, rowNum) -> rs.getLong("id"), bankId, type);
        Collections.shuffle(ids);
        int inserted = 0;
        for (Long questionId : ids) {
            if (selected.contains(questionId)) continue;
            jdbc.update("INSERT INTO assessment_attempt_question(attempt_id,question_id,score,sort_no) VALUES(?,?,?,?)", attemptId, questionId, score, sortNo++);
            selected.add(questionId);
            if (++inserted >= count) break;
        }
        if (inserted < count) throw new BusinessException(ErrorCode.BAD_REQUEST, "随机组卷可用题目不足，请重新配置题库");
        return sortNo;
    }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); }
    private static BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value)); }
    private void ensureTaskOpen(Map<String, Object> task) {
        String status = String.valueOf(task.getOrDefault("taskStatus", "PUBLISHED"));
        if ("ENDED".equals(status) || "DISABLED".equals(status)) throw new BusinessException(ErrorCode.BAD_REQUEST, "考试已结束，不能继续答题");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = toLocalDateTime(task.get("startAt"));
        LocalDateTime endAt = toLocalDateTime(task.get("endAt"));
        if (startAt != null && now.isBefore(startAt)) throw new BusinessException(ErrorCode.BAD_REQUEST, "考试尚未开始");
        if (endAt != null && !now.isBefore(endAt)) throw new BusinessException(ErrorCode.BAD_REQUEST, "考试已结束，不能继续答题");
    }
    private Map<String, Object> ensureAttemptOpen(Long attemptId) {
        Map<String, Object> attempt;
        try {
            attempt = jdbc.queryForMap("SELECT a.id,a.status,t.status taskStatus,t.start_at startAt,t.end_at endAt FROM assessment_attempt a JOIN assessment_task t ON t.id=a.task_id WHERE a.id=?", attemptId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "答题记录不存在");
        }
        if (!"IN_PROGRESS".equals(String.valueOf(attempt.get("status")))) throw new BusinessException(ErrorCode.BAD_REQUEST, "该答题记录已结束，不能继续答题");
        ensureTaskOpen(attempt);
        return attempt;
    }
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
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
        return value instanceof Boolean flag
                ? flag
                : value != null && ("1".equals(String.valueOf(value)) || Boolean.parseBoolean(String.valueOf(value)));
    }
    /** 将数据库中的布尔字段统一转换为 Java boolean。 */
    private static boolean flag(Object value) {
        return value instanceof Boolean bool
                ? bool
                : value != null && ("1".equals(String.valueOf(value))
                || "true".equalsIgnoreCase(String.valueOf(value)));
    }
    private static List<String> answerList(Object value, String type) { String raw = value == null ? "" : String.valueOf(value).trim(); if (raw.isBlank()) return List.of(); if ("TRUE_FALSE".equals(type)) return List.of(raw.equalsIgnoreCase("TRUE") ? "A" : "B"); if ("MULTIPLE".equals(type) && raw.matches("[A-Za-z]+")) return raw.toUpperCase(Locale.ROOT).chars().mapToObj(c -> String.valueOf((char) c)).toList(); return Arrays.stream(raw.split("[,，、\\s]+")).filter(s -> !s.isBlank()).toList(); }
    private static String kind(String type) { return switch (type) { case "MULTIPLE" -> "multi"; case "TRUE_FALSE" -> "judge"; default -> "single"; }; }
    /**
     * 按题型判断答案：单选、判断题要求答案一致；多选题只要命中全部标准答案即可，
     * 不受选项顺序影响，也不因额外选择其他选项而判错。
     */
    private static boolean matchesAnswer(String type, String answer, String correct) {
        if ("MULTIPLE".equals(type)) {
            Set<String> selected = new HashSet<>(answerList(answer, type));
            Set<String> required = new HashSet<>(answerList(correct, type));
            return !selected.isEmpty() && !required.isEmpty() && selected.containsAll(required);
        }
        return normalize(answer).equals(normalize(correct));
    }

    /** 统一单选、判断题答案格式。 */
    private static String normalize(String value) {
        String normalized = value == null ? "" : value.replaceAll("[，、,\\s]+", "").toUpperCase(Locale.ROOT);
        if (normalized.matches("[A-Z]+")) {
            return normalized.chars().sorted()
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        }
        return normalized;
    }
    public record CandidateCommand(String name, String phone) {}
    public record AnswerCommand(String answer) {}
}
