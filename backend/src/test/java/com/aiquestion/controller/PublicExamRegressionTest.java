package com.aiquestion.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class PublicExamRegressionTest {
    @Test void reviewFlagNeverRevealsAnswersBeforeSubmission() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1L)))
            .thenReturn(java.util.List.of(new java.util.HashMap<>(java.util.Map.of("reviewEnabled", true))), java.util.List.of());
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
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(7L);
        org.mockito.Mockito.when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("NOT EXISTS"),org.mockito.ArgumentMatchers.eq(Integer.class),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.eq("Alice"),org.mockito.ArgumentMatchers.eq("13800000000"))).thenReturn(1);
        var error=assertThrows(com.aiquestion.common.BusinessException.class,()->new PublicExamController(jdbc).start(1L,new PublicExamController.CandidateCommand("Alice","13800000000")));
        assertEquals("请先完成全部培训视频学习",error.getMessage());
        org.mockito.Mockito.verify(jdbc,org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO assessment_attempt("),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }
}
