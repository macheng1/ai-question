package com.aiquestion.service;

import com.aiquestion.security.AccountIdentity;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.aiquestion.repository.LearnerRecordRepository;

/**
 * Read-only learner views. Existing candidate learning and exam submission routes remain unchanged.
 */
@Service
public class LearnerRecordService {

    private final LearnerRecordRepository persistence;

    private final AccountService accounts;

    public LearnerRecordService(LearnerRecordRepository persistence, AccountService accounts) {
        this.persistence = persistence;
        this.accounts = accounts;
    }

    public List<Map<String, Object>> trainings(AccountIdentity identity) {
        accounts.employee(identity.accountId());
        var rows = persistence.findAssignedTrainings(identity.accountId(), identity.enterpriseId());
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        for (var row : rows) {
            normalizeTime(row, "videoCompletedAt");
            normalizeTime(row, "readingCompletedAt");
            String learning = LearnerTaskStatus.learning(row.get("sessionId") != null, number(row, "totalUnits"), number(row, "completedUnits"), row.get("confirmedAt") != null);
            row.put("learningStatus", learning);
            row.put("started", row.get("sessionId") != null);
            row.put("progress", number(row, "totalUnits") == 0 ? (learning.equals("COMPLETED") ? 100 : 0) : Math.min(100, number(row, "completedUnits") * 100 / number(row, "totalUnits")));
            Object completedAt = number(row, "totalUnits") == 0 ? row.get("confirmedAt") : null;
            if (completedAt == null) {
                var video = date(row.get("videoCompletedAt"));
                var reading = date(row.get("readingCompletedAt"));
                completedAt = video == null ? row.get("readingCompletedAt") : reading == null || video.isAfter(reading) ? row.get("videoCompletedAt") : row.get("readingCompletedAt");
            }
            row.put("completedAt", learning.equals("COMPLETED") ? completedAt : null);
            row.remove("videoCompletedAt");
            row.remove("readingCompletedAt");
            row.remove("confirmedAt");
            row.put("trainingEnded", flag(row.get("trainingEnded")));
            row.put("purpose", row.get("assessmentId") == null ? "STANDALONE" : "EXAM");
            if (row.get("assessmentId") != null) {
                var start = date(row.get("examStartAt"));
                var end = date(row.get("examEndAt"));
                boolean disabled = "DISABLED".equals(row.get("examLifecycle"));
                boolean closed = disabled || "ENDED".equals(row.get("examLifecycle")) || (end != null && !now.isBefore(end));
                boolean published = !"DRAFT".equals(row.get("examLifecycle")) && (start == null || !now.isBefore(start));
                boolean submitted = "SUBMITTED".equals(row.get("attemptStatus"));
                row.put("examStatus", "PENDING_REVIEW".equals(row.get("attemptStatus")) ? "PENDING_REVIEW" : LearnerTaskStatus.exam(learning.equals("COMPLETED"), row.get("attemptId") != null && !submitted, submitted, flag(row.get("resultVisible")), flag(row.get("passed")), closed, published));
                row.put("canRetake", submitted && published && LearnerTaskStatus.canRetake(flag(row.get("allowRetake")), (int) number(row, "maxAttempts"), (int) number(row, "attemptCount"), start, end, closed, now));
                row.put("remainingAttempts", Math.max(0, (flag(row.get("allowRetake")) ? number(row, "maxAttempts") : 1) - number(row, "attemptCount")));
                if (!flag(row.get("resultVisible")) || !submitted) {
                    row.put("score", null);
                    row.put("passed", null);
                }
            }
            row.remove("resultVisible");
        }
        return rows;
    }

    private static void normalizeTime(Map<String, Object> row, String key) {
        if (row.get(key) instanceof Number millis)
            row.put(key, new java.sql.Timestamp(millis.longValue()));
    }

    private static long number(Map<String, Object> row, String key) {
        return row.get(key) instanceof Number n ? n.longValue() : 0;
    }

    private static boolean flag(Object value) {
        return Boolean.TRUE.equals(value) || "1".equals(String.valueOf(value));
    }

    private static java.time.LocalDateTime date(Object value) {
        if (value instanceof java.sql.Timestamp t)
            return java.time.LocalDateTime.ofInstant(t.toInstant(), java.time.ZoneOffset.UTC);
        if (value instanceof java.time.LocalDateTime t)
            return t;
        return null;
    }

    public Map<String, Object> detail(AccountIdentity identity, long trainingId) {
        var training = trainings(identity).stream().filter(r -> ((Number) r.get("id")).longValue() == trainingId).findFirst().orElseThrow(() -> new com.aiquestion.common.BusinessException(com.aiquestion.common.ErrorCode.NOT_FOUND, "培训不存在或未分配给你"));
        Object session = training.get("sessionId");
        var materials = persistence.findTrainingMaterials(com.aiquestion.repository.RepositoryRows.nullableLong(session), trainingId, com.aiquestion.repository.RepositoryRows.nullableLong(session), trainingId);
        materials.forEach(row -> normalizeTime(row, "completedAt"));
        var attempts = exams(identity).stream().filter(r -> Objects.equals(r.get("taskId"), training.get("assessmentId"))).toList();
        return Map.of("training", training, "materials", materials, "attempts", attempts);
    }

    public List<Map<String, Object>> learningRecords(AccountIdentity identity) {
        return trainings(identity).stream().filter(r -> r.get("sessionId") != null).map(r -> {
            var result = new LinkedHashMap<>(r);
            result.put("trainingId", r.get("id"));
            result.put("completed", "COMPLETED".equals(r.get("learningStatus")));
            return (Map<String, Object>) result;
        }).toList();
    }

    public List<Map<String, Object>> exams(AccountIdentity identity) {
        accounts.employee(identity.accountId());
        return persistence.findExamRecords(identity.enterpriseId(), identity.accountId()).stream().map(row -> {
            if (!"SUBMITTED".equals(row.get("status")) || !flag(row.get("showResultImmediately"))) {
                row.put("score", null);
                row.put("passed", null);
            }
            return row;
        }).toList();
    }

    public List<Map<String, Object>> mistakes(AccountIdentity identity) {
        accounts.employee(identity.accountId());
        var mistakes = persistence.findLatestMistakes(identity.enterpriseId(), identity.accountId());
        if (mistakes.isEmpty())
            return mistakes;
        var questionIds = mistakes.stream().map(row -> row.get("questionId")).distinct().toList();
        var options = persistence.findQuestionOptions(questionIds);
        var optionsByQuestion = options.stream().collect(Collectors.groupingBy(row -> row.get("questionId")));
        mistakes.forEach(row -> row.put("options", optionsByQuestion.getOrDefault(row.get("questionId"), List.of())));
        return mistakes;
    }
}
