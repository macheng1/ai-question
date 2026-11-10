package com.aiquestion.service;

import java.time.LocalDateTime;

/** Employee-facing status rules. Actual entry/retake authorization stays in the exam service. */
public final class LearnerTaskStatus {
  private LearnerTaskStatus() {}

  public static String learning(boolean started, long total, long completed, boolean confirmed) {
    if (!started) return "NOT_STARTED";
    return (total == 0 ? confirmed : completed >= total) ? "COMPLETED" : "IN_PROGRESS";
  }

  public static String exam(
      boolean learningComplete,
      boolean answering,
      boolean submitted,
      boolean resultVisible,
      boolean passed,
      boolean closed,
      boolean started) {
    if (submitted) return !resultVisible ? "RESULT_PENDING" : passed ? "PASSED" : "FAILED";
    if (closed) return "ENDED";
    if (answering) return "IN_PROGRESS";
    if (!started) return "NOT_STARTED";
    return learningComplete ? "READY" : "WAITING_LEARNING";
  }

  public static boolean canRetake(
      boolean allowed,
      int maximum,
      int used,
      LocalDateTime start,
      LocalDateTime end,
      boolean disabled,
      LocalDateTime now) {
    return allowed
        && used < maximum
        && !disabled
        && (start == null || !now.isBefore(start))
        && (end == null || now.isBefore(end));
  }
}
