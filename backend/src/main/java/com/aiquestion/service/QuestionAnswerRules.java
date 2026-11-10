package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.controller.QuestionItemController.OptionRequest;
import java.util.*;

/** 手工录题与模板导入共用的答案校验，避免生成无法正确作答的题目。 */
public final class QuestionAnswerRules {
  private QuestionAnswerRules() {}

  public static String validate(String type, String answer, List<OptionRequest> options) {
    String normalized =
        Objects.toString(answer, "").replaceAll("[，、,\\s]+", "").toUpperCase(Locale.ROOT);
    if ("TRUE_FALSE".equals(type)) {
      if (!Set.of("TRUE", "FALSE").contains(normalized)) fail("判断题答案必须为正确或错误");
      return normalized;
    }
    if (!Set.of("SINGLE", "MULTIPLE").contains(type)) fail("题型无效");
    if (options == null || options.size() < 2) fail("选择题至少需要两个选项");
    Set<String> keys = new HashSet<>();
    for (OptionRequest option : options) {
      if (option == null
          || option.optionKey() == null
          || !option.optionKey().matches("[A-Z]")
          || option.optionText() == null
          || option.optionText().isBlank()
          || !keys.add(option.optionKey())) {
        fail("选项内容不能为空，且选项标识不能重复");
      }
    }
    if (!normalized.matches("[A-Z]+")) fail("请选择正确答案");
    if ("SINGLE".equals(type) && normalized.length() != 1) fail("单选题只能有一个正确答案");
    Set<String> selected = new LinkedHashSet<>();
    for (char key : normalized.toCharArray()) {
      if (!keys.contains(String.valueOf(key))) fail("正确答案包含不存在的选项");
      selected.add(String.valueOf(key));
    }
    return String.join(",", selected);
  }

  private static void fail(String message) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, message);
  }
}
