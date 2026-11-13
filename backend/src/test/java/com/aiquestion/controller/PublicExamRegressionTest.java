package com.aiquestion.controller;

import com.aiquestion.common.*;
import com.aiquestion.repository.*;
import com.aiquestion.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicExamRegressionTest {
    private final PublicExamRepository repository = mock(PublicExamRepository.class);
    private final AssessmentRosterService roster = mock(AssessmentRosterService.class);
    private final CandidateLearningService learning = mock(CandidateLearningService.class);
    private final PublicExamService service = new PublicExamService(repository, roster, learning);

    private void attempt(String status) {
        when(repository.findAttemptIdentity(1L)).thenReturn(List.of(Map.of("task_id", 2L, "candidate_name", "Alice", "phone", "13900000000")));
        when(repository.lockAttempt(1L)).thenReturn(List.of(Map.of("status", status, "duration", 30)));
    }

    @Test void trainingDeadlineBlocksAnswersEvenWhenExamDeadlineIsLater() {
        attempt("IN_PROGRESS");
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        when(repository.lockAttempt(1L)).thenReturn(List.of(Map.of("status", "IN_PROGRESS", "duration", 30,
            "endAt", now.plusDays(1), "trainingEndAt", now.minusSeconds(1))));
        var error = assertThrows(BusinessException.class, () -> service.answer(1L, 999L, new PublicExamController.AnswerCommand("A")));
        assertEquals("培训已结束，不能继续学习或考试", error.getMessage());
    }
    @Test void aggregateTrainingTimesUseAbsoluteInstants() {
        long epoch = java.time.Instant.parse("2026-11-12T07:52:00Z").toEpochMilli();
        when(repository.findTaskDetails(1L)).thenReturn(List.of(Map.of("trainingStartAt", new java.math.BigDecimal(epoch))));
        var data = service.get(1L, false, null).data();
        assertEquals(java.time.Instant.parse("2026-11-12T07:52:00Z"), ((java.sql.Timestamp)data.get("trainingStartAt")).toInstant());
        assertEquals(java.time.LocalDateTime.parse("2026-11-12T07:52:00"),
            ReflectionTestUtils.invokeMethod(PublicExamService.class, "toLocalDateTime", new java.math.BigDecimal(epoch)));
    }
    @Test void foreignQuestionReturnsBusinessError() {
        attempt("IN_PROGRESS");
        var error = assertThrows(BusinessException.class, () -> service.answer(1L, 999L, new PublicExamController.AnswerCommand("A")));
        assertEquals("题目不属于本次考试", error.getMessage());
    }
    @Test void missingAttemptReturnsBusinessErrorInsteadOfServerError() {
        var error = assertThrows(BusinessException.class, () -> service.submit(999L));
        assertEquals("答题记录不存在", error.getMessage());
    }
    @Test void reviewFlagNeverRevealsAnswersBeforeSubmission() {
        when(repository.findTaskDetails(1L)).thenReturn(List.of(new HashMap<>(Map.of("reviewEnabled", true))));
        var result = service.get(1L, true, null);
        assertEquals(false, result.data().get("showAnswers"));
        assertEquals(List.of(), result.data().get("questions"));
    }
    @Test void attemptMustBelongToRequestedTask() {
        when(repository.findTaskDetails(1L)).thenReturn(List.of(new HashMap<>(Map.of("reviewEnabled", true))));
        assertThrows(BusinessException.class, () -> service.get(1L, true, 2L));
    }
    @Test void jdbcTimestampComparisonUsesInstantRatherThanJvmWallClock() {
        var instant = java.time.Instant.parse("2026-10-28T14:45:00Z");
        assertEquals(java.time.LocalDateTime.parse("2026-10-28T14:45:00"),
            ReflectionTestUtils.invokeMethod(PublicExamService.class, "toLocalDateTime", java.sql.Timestamp.from(instant)));
    }
    @Test void elapsedDurationExpiresAttempt() {
        var attempt = Map.<String,Object>of("startedAt", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(61), "duration", 60);
        assertEquals(true, ReflectionTestUtils.invokeMethod(PublicExamService.class, "expired", attempt));
    }
    @Test void repeatedSubmissionReturnsExistingResultWithoutUpdatingScore() {
        attempt("SUBMITTED");
        var tested = spy(service);
        var expected = ApiResponse.ok(Map.<String,Object>of("score", 80));
        doReturn(expected).when(tested).result(1L);
        assertSame(expected, tested.submit(1L));
        verify(repository, never()).submitAttempt(any(), anyBoolean(), any());
    }
    @Test void taskEndExpiresBeforePersonalDuration() {
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        var attempt = Map.<String,Object>of("startedAt", now.minusMinutes(1), "duration", 60, "endAt", now.minusSeconds(1));
        assertEquals(true, ReflectionTestUtils.invokeMethod(PublicExamService.class, "expired", attempt));
    }
    @Test void multipleChoiceRejectsExtraSelections() {
        assertEquals(false, ReflectionTestUtils.invokeMethod(PublicExamService.class, "matchesAnswer", "MULTIPLE", "A,B,C", "AB"));
        assertEquals(true, ReflectionTestUtils.invokeMethod(PublicExamService.class, "matchesAnswer", "MULTIPLE", "B,A", "AB"));
    }
    @Test void newAttemptCannotBypassLearning() {
        when(repository.findTaskDetails(1L)).thenReturn(List.of(Map.of("maxAttempts", 1)));
        doAnswer(call -> { call.getArgument(3, GeneratedId.class).setId(7L); return 1; })
            .when(repository).insertParticipant(anyLong(), anyString(), anyString(), any());
        doThrow(new BusinessException(ErrorCode.BAD_REQUEST, "请先完成全部培训材料学习"))
            .when(learning).requireComplete(1L, "Alice", "13800000000");
        var error = assertThrows(BusinessException.class, () -> service.start(1L, new PublicExamController.CandidateCommand("Alice", "13800000000")));
        assertEquals("请先完成全部培训材料学习", error.getMessage());
        verify(repository, never()).insertAttemptFromTask(any(), any(), anyInt(), any(), any());
    }
    @Test void randomSnapshotCanShuffleMultipleAvailableQuestions() {
        when(repository.findEnabledQuestionIds(3L, "SINGLE")).thenReturn(List.of(Map.of("id", 11L), Map.of("id", 12L)));
        Integer next = assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "snapshotType",
            1L, 3L, "SINGLE", 1, java.math.BigDecimal.TEN, 0, new HashSet<Long>()));
        assertEquals(1, next);
        verify(repository, times(1)).insertSnapshotQuestion(eq(1L), anyLong(), eq(java.math.BigDecimal.TEN), eq(0));
    }

    @Test void progressRejectsCandidateOutsideRoster() {
        when(repository.findTaskDetails(1L)).thenReturn(List.of(Map.of("reviewEnabled", false)));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "你不在本培训的学习范围内"))
            .when(roster).requireEligible(1L, "非名单人员", "13900009999");
        var error = assertThrows(BusinessException.class, () -> service.progress(1L, "非名单人员", "13900009999"));
        assertEquals("你不在本培训的学习范围内", error.getMessage());
        verify(repository, never()).findCandidateActiveAttempt(any(), any(), any());
    }
}
