package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.util.*;

/** 培训评估没有正确答案，独立校验题型与填写内容。 */
public final class TrainingSurveyRules {
  private TrainingSurveyRules() {}
  public record Question(String id, String type, String title, boolean required, List<String> options) {}
  private static void require(boolean condition, String message) {
    if (!condition) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
  }
  public static void questions(List<Question> questions) {
    require(questions != null && questions.size() <= 50, "评估最多支持50道题");
    Set<String> ids = new HashSet<>();
    for (var q : questions) {
      require(q != null, "题目不能为空");
      require(q.id() != null && q.id().matches("[a-zA-Z0-9_-]{1,80}") && ids.add(q.id()), "题目标识无效或重复");
      require(Set.of("RATING", "SINGLE", "TEXT").contains(q.type() == null ? "" : q.type()), "不支持的评估题型");
      require(q.title() != null && !q.title().isBlank() && q.title().length() <= 300, "题目标题须为1至300字");
      require(q.options() != null, "选项不能为空");
      if ("SINGLE".equals(q.type())) {
        require(q.options().size() >= 2 && q.options().size() <= 20, "单选题需要2至20个选项");
        Set<String> options = new HashSet<>();
        for (var option : q.options()) require(option != null && !option.isBlank() && option.length() <= 200 && options.add(option.trim()), "选项不能为空、重复或超过200字");
      } else require(q.options().isEmpty(), "评分题与意见题无需选项");
    }
  }
  public static void answers(List<Question> questions, Map<String, String> answers) {
    require(answers != null, "请填写评估");
    Set<String> ids = new HashSet<>();
    for (var q : questions) {
      ids.add(q.id());
      String value = answers.get(q.id());
      if (value == null || value.isBlank()) { require(!q.required(), "请填写：" + q.title()); continue; }
      switch (q.type()) {
        case "RATING" -> require(value.matches("[1-5]"), "评分必须为1至5分");
        case "SINGLE" -> require(q.options().contains(value), "请选择有效选项");
        case "TEXT" -> require(value.length() <= 1000, "意见不能超过1000字");
        default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的评估题型");
      }
    }
    require(ids.containsAll(answers.keySet()), "答案包含未知题目");
  }
}
