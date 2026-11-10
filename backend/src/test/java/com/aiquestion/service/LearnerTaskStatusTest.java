package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LearnerTaskStatusTest {
  @Test
  void addedMaterialsInvalidatePriorCompletion() {
    assertEquals("IN_PROGRESS", LearnerTaskStatus.learning(true, 4, 3, true));
  }

  @Test
  void assignmentWithoutActivityIsVisibleAsNotStarted() {
    assertEquals("NOT_STARTED", LearnerTaskStatus.learning(false, 3, 0, false));
  }

  @Test
  void emptyMaterialListRequiresExplicitCompletion() {
    assertEquals("IN_PROGRESS", LearnerTaskStatus.learning(true, 0, 0, false));
    assertEquals("COMPLETED", LearnerTaskStatus.learning(true, 0, 0, true));
  }

  @Test
  void materialsDetermineCompletion() {
    assertEquals("IN_PROGRESS", LearnerTaskStatus.learning(true, 3, 2, false));
    assertEquals("COMPLETED", LearnerTaskStatus.learning(true, 3, 3, false));
  }

  @Test
  void unpublishedResultNeverLeaksPassOrFail() {
    assertEquals(
        "RESULT_PENDING", LearnerTaskStatus.exam(true, false, true, false, false, false, true));
  }

  @Test
  void completionAndExamAreIndependent() {
    assertEquals(
        "WAITING_LEARNING", LearnerTaskStatus.exam(false, false, false, true, false, false, true));
    assertEquals("READY", LearnerTaskStatus.exam(true, false, false, true, false, false, true));
  }

  @Test
  void submittedResultSurvivesExamClosing() {
    assertEquals("PASSED", LearnerTaskStatus.exam(true, false, true, true, true, true, true));
  }

  @Test
  void retakeRequiresTimeAndAttempts() {
    var now = LocalDateTime.now();
    assertTrue(
        LearnerTaskStatus.canRetake(true, 3, 1, now.minusHours(1), now.plusHours(1), false, now));
    assertFalse(
        LearnerTaskStatus.canRetake(true, 1, 1, now.minusHours(1), now.plusHours(1), false, now));
    assertFalse(
        LearnerTaskStatus.canRetake(true, 3, 1, now.minusHours(2), now.minusHours(1), false, now));
  }
}
