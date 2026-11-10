package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TrainingReminderRulesTest {
  private final LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0);

  @Test
  void deadlinesAreIndependent() {
    assertEquals(
        java.util.List.of("培训逾期未完成"),
        TrainingReminderRules.reasons(now.minusMinutes(1), false, now.plusHours(1), false, now));
    assertEquals(
        java.util.List.of("考试逾期未通过"),
        TrainingReminderRules.reasons(now.plusHours(1), false, now.minusMinutes(1), false, now));
  }

  @Test
  void includesBothReasonsButNotCompletedWork() {
    assertEquals(
        2,
        TrainingReminderRules.reasons(now.minusHours(1), false, now.minusHours(1), false, now)
            .size());
    assertTrue(
        TrainingReminderRules.reasons(now.minusHours(1), true, now.minusHours(1), true, now)
            .isEmpty());
  }

  @Test
  void unsetAndExactDeadlineAreNotOverdue() {
    assertTrue(TrainingReminderRules.reasons(null, false, null, false, now).isEmpty());
    assertTrue(TrainingReminderRules.reasons(now, false, now, false, now).isEmpty());
  }
}
