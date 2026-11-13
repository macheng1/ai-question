package com.aiquestion.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 按培训与考试的截止时间判断逾期；满意度反馈不参与完成判定。 */
public final class TrainingReminderRules {
  private TrainingReminderRules() {}

  public static List<String> reasons(
      LocalDateTime trainingEnd,
      boolean learned,
      LocalDateTime examEnd,
      boolean passed,
      LocalDateTime now) {
    var reasons = new ArrayList<String>();
    if (trainingEnd != null && now.isAfter(trainingEnd) && !learned) reasons.add("培训逾期未完成");
    if (examEnd != null && now.isAfter(examEnd) && !passed) reasons.add("考试逾期未通过");
    return List.copyOf(reasons);
  }
}
