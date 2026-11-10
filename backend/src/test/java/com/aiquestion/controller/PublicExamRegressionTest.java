package com.aiquestion.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class PublicExamRegressionTest {
    @Test void foreignQuestionReturnsBusinessError() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForMap(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L)))
            .thenReturn(java.util.Map.of("task_id", 2L, "candidate_name", "Alice", "phone", "13900000000"),
                java.util.Map.of("status", "IN_PROGRESS", "duration", 30));
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=?", Integer.class, 2L)).thenReturn(1);
        org.mockito.Mockito.when(jdbc.queryForMap(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(999L)))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        var error = assertThrows(com.aiquestion.common.BusinessException.class,
            () -> new PublicExamController(jdbc).answer(1L, 999L, new PublicExamController.AnswerCommand("A")));
        assertEquals("题目不属于本次考试", error.getMessage());
    }
    @Test void missingAttemptReturnsBusinessErrorInsteadOfServerError() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForMap(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(999L)))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        var error = assertThrows(com.aiquestion.common.BusinessException.class,
            () -> new PublicExamController(jdbc).submit(999L));
        assertEquals("答题记录不存在", error.getMessage());
    }
    @Test void reviewFlagNeverRevealsAnswersBeforeSubmission() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L)))
            .thenReturn(java.util.List.of(new java.util.HashMap<>(java.util.Map.of("reviewEnabled", true))), java.util.List.of());
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE assessment_id=?",Integer.class,1L)).thenReturn(0);
        var result = new PublicExamController(jdbc).get(1L, true, null);
        assertEquals(false, result.data().get("showAnswers"));
    }
    @Test void attemptMustBelongToRequestedTask() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L)))
            .thenReturn(java.util.List.of(new java.util.HashMap<>(java.util.Map.of("reviewEnabled", true))));
        assertThrows(com.aiquestion.common.BusinessException.class, () -> new PublicExamController(jdbc).get(1L, true, 2L));
    }
    @Test void jdbcTimestampComparisonUsesInstantRatherThanJvmWallClock() {
        var instant = java.time.Instant.parse("2026-10-28T14:45:00Z");
        assertEquals(java.time.LocalDateTime.parse("2026-10-28T14:45:00"),
            ReflectionTestUtils.invokeMethod(PublicExamController.class, "toLocalDateTime", java.sql.Timestamp.from(instant)));
    }
    @Test void elapsedDurationExpiresAttempt() {
        var attempt = java.util.Map.<String,Object>of("startedAt", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(61), "duration", 60);
        assertEquals(true, ReflectionTestUtils.invokeMethod(PublicExamController.class, "expired", attempt));
    }

    @Test void multipleChoiceRejectsExtraSelections() {
        assertEquals(false, ReflectionTestUtils.invokeMethod(PublicExamController.class, "matchesAnswer", "MULTIPLE", "A,B,C", "AB"));
        assertEquals(true, ReflectionTestUtils.invokeMethod(PublicExamController.class, "matchesAnswer", "MULTIPLE", "B,A", "AB"));
    }
    @Test void newAttemptCannotBypassLearning() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L)))
            .thenReturn(java.util.List.of(java.util.Map.of("maxAttempts",1)));
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=?",Integer.class,1L)).thenReturn(1);
        org.mockito.Mockito.when(jdbc.queryForList("SELECT id FROM training WHERE assessment_id=?",Long.class,1L)).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(7L);
        org.mockito.Mockito.when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("NOT EXISTS"),org.mockito.ArgumentMatchers.eq(Integer.class),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.eq("Alice"),org.mockito.ArgumentMatchers.eq("13800000000"))).thenReturn(1);
        var error=assertThrows(com.aiquestion.common.BusinessException.class,()->new PublicExamController(jdbc).start(1L,new PublicExamController.CandidateCommand("Alice","13800000000")));
        assertEquals("请先完成全部培训材料学习",error.getMessage());
        org.mockito.Mockito.verify(jdbc,org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO assessment_attempt("),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }
    @Test void progressRejectsCandidateOutsideRoster() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L)))
            .thenReturn(java.util.List.of(java.util.Map.of("reviewEnabled", false)));
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=?",Integer.class,1L)).thenReturn(1);
        org.mockito.Mockito.when(jdbc.queryForList("SELECT id FROM training WHERE assessment_id=?",Long.class,1L)).thenReturn(java.util.List.of(2L));
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE training_id=? AND candidate_name=? AND phone=?", Integer.class, 2L, "非名单人员", "13900009999")).thenReturn(0);
        var error = assertThrows(com.aiquestion.common.BusinessException.class,
            () -> new PublicExamController(jdbc).progress(1L, "非名单人员", "13900009999"));
        assertEquals("你不在本培训的学习范围内", error.getMessage());
    }
}
