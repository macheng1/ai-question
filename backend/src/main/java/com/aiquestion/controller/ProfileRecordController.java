package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 当前登录人的考试记录与错题查询接口。正式记录只读数据库，不依赖浏览器缓存。 */
@Tag(name = "个人记录")
@RestController
@RequestMapping("/profile")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class ProfileRecordController {
    private final JdbcTemplate jdbcTemplate;
    private final JwtTokenService tokenService;

    public ProfileRecordController(JdbcTemplate jdbcTemplate, JwtTokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    @Operation(summary = "查询我的考试记录")
    @GetMapping("/records")
    public ApiResponse<List<Map<String, Object>>> records(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Identity identity = identity(authorization);
        String sql = """
                SELECT a.id AS attemptId, a.task_id AS taskId, t.name AS examName,
                       p.name AS paperName, a.score AS score, a.total_score AS totalScore,
                       p.pass_score AS passScore, a.passed AS passed, a.status AS status,
                       a.attempt_no AS attemptNo, a.started_at AS startedAt,
                       a.submitted_at AS submittedAt,
                       CASE WHEN a.started_at IS NULL THEN 0 ELSE TIMESTAMPDIFF(SECOND,
                         a.started_at, COALESCE(a.submitted_at, CURRENT_TIMESTAMP(3))) END AS durationSeconds,
                       ap.candidate_name AS candidateName, ap.phone AS phone
                FROM assessment_attempt a
                JOIN assessment_participant ap ON ap.id = a.participant_id
                JOIN assessment_task t ON t.id = a.task_id
                JOIN test_paper p ON p.id = t.paper_id
                WHERE (ap.user_id = ? OR (ap.user_id IS NULL AND ap.phone = ?))
                  AND a.status IN ('SUBMITTED', 'IN_PROGRESS')
                ORDER BY COALESCE(a.submitted_at, a.started_at) DESC, a.id DESC
                """;
        return ApiResponse.ok(jdbcTemplate.queryForList(sql, identity.userId(), identity.phone()));
    }

    @Operation(summary = "查询我的错题")
    @GetMapping("/mistakes")
    public ApiResponse<List<Map<String, Object>>> mistakes(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Identity identity = identity(authorization);
        String sql = """
                SELECT aa.attempt_id AS attemptId, aa.question_id AS questionId,
                       a.task_id AS taskId, t.name AS examName, COALESCE(qb.name, '') AS bankName,
                       qi.question_type AS questionType, qi.stem AS stem,
                       qi.answer_text AS correctAnswer, qi.analysis AS analysis,
                       aa.answer_text AS userAnswer, aa.answered_at AS answeredAt
                FROM assessment_answer aa
                JOIN assessment_attempt a ON a.id = aa.attempt_id
                JOIN assessment_participant ap ON ap.id = a.participant_id
                JOIN assessment_task t ON t.id = a.task_id
                JOIN question_item qi ON qi.id = aa.question_id
                LEFT JOIN question_bank qb ON qb.id = qi.bank_id
                WHERE COALESCE(aa.is_correct, 0) = 0 AND a.status = 'SUBMITTED'
                  AND a.id = (
                    SELECT MAX(a2.id)
                    FROM assessment_attempt a2
                    JOIN assessment_participant ap2 ON ap2.id = a2.participant_id
                    WHERE a2.task_id = a.task_id
                      AND a2.status = 'SUBMITTED'
                      AND (ap2.user_id = ? OR (ap2.user_id IS NULL AND ap2.phone = ?))
                  )
                  AND (ap.user_id = ? OR (ap.user_id IS NULL AND ap.phone = ?))
                ORDER BY aa.answered_at DESC, aa.id DESC
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, identity.userId(), identity.phone(), identity.userId(), identity.phone());
        rows.forEach(row -> row.put("options", options(row.get("questionId"))));
        return ApiResponse.ok(rows);
    }

    private List<Map<String, Object>> options(Object questionId) {
        if (questionId == null) { return List.of(); }
        return jdbcTemplate.queryForList(
                "SELECT option_key AS `key`, option_text AS text FROM question_option WHERE question_id = ? ORDER BY sort_no, id",
                questionId);
    }

    private Identity identity(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            Long userId = Long.valueOf(tokenService.subject(authorization.substring(7), false));
            List<String> phones = jdbcTemplate.query(
                    "SELECT phone FROM app_user WHERE id = ?", (rs, rowNum) -> rs.getString(1), userId);
            if (phones.isEmpty()) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
            return new Identity(userId, phones.get(0));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private record Identity(Long userId, String phone) {}
}
