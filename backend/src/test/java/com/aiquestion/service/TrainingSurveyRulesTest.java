package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainingSurveyRulesTest {
  private TrainingSurveyRules.Question question(String type, boolean required, List<String> options) {
    return new TrainingSurveyRules.Question("q1", type, "满意吗", required, options);
  }
  @Test void rejectsInvalidRatingAndMissingRequiredAnswer() {
    var questions = List.of(question("RATING", true, List.of()));
    assertThrows(BusinessException.class, () -> TrainingSurveyRules.answers(questions, Map.of()));
    assertThrows(BusinessException.class, () -> TrainingSurveyRules.answers(questions, Map.of("q1", "6")));
    assertDoesNotThrow(() -> TrainingSurveyRules.answers(questions, Map.of("q1", "5")));
  }
  @Test void rejectsForgedOptionAndUnknownQuestion() {
    var questions = List.of(question("SINGLE", true, List.of("满意", "一般")));
    assertThrows(BusinessException.class, () -> TrainingSurveyRules.answers(questions, Map.of("q1", "其他")));
    assertThrows(BusinessException.class, () -> TrainingSurveyRules.answers(questions, Map.of("q1", "满意", "fake", "x")));
  }
  @Test void rejectsDuplicateOptionsIdsAndUnsupportedType() {
    assertThrows(BusinessException.class, () -> TrainingSurveyRules.questions(List.of(question("SINGLE", true, List.of("同", "同")))));
    var q = question("TEXT", false, List.of());
    assertThrows(BusinessException.class, () -> TrainingSurveyRules.questions(List.of(q, q)));
    assertThrows(BusinessException.class, () -> TrainingSurveyRules.questions(List.of(question("EXAM", true, List.of()))));
  }
  @Test void optionalTextMayBeEmptyButNotOversized() {
    var questions = List.of(question("TEXT", false, List.of()));
    assertDoesNotThrow(() -> TrainingSurveyRules.answers(questions, Map.of()));
    assertThrows(BusinessException.class, () -> TrainingSurveyRules.answers(questions, Map.of("q1", "a".repeat(1001))));
  }
}
