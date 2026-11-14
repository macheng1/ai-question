package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;

import com.aiquestion.common.BusinessException;
import com.aiquestion.controller.QuestionItemController.OptionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionAnswerRulesTest {
  private final List<OptionRequest> options =
      List.of(new OptionRequest("A", "报警", true, 0), new OptionRequest("B", "疏散", true, 1));

  @Test
  void singleChoiceCannotHaveMultipleAnswers() {
    assertThrows(
        BusinessException.class, () -> QuestionAnswerRules.validate("SINGLE", "AB", options));
  }

  @Test
  void missingOptionCannotBeCorrect() {
    assertThrows(
        BusinessException.class, () -> QuestionAnswerRules.validate("MULTIPLE", "AC", options));
  }

  @Test
  void shortAnswerKeepsReferenceTextAndRejectsBlankTextOrOptions() {
    String reference = "  Act first, then report.\n记录时间、地点。 ";
    assertEquals(reference, QuestionAnswerRules.validate("SHORT_ANSWER", reference, List.of()));
    assertThrows(BusinessException.class, () -> QuestionAnswerRules.validate("SHORT_ANSWER", "  ", List.of()));
    assertThrows(BusinessException.class, () -> QuestionAnswerRules.validate("SHORT_ANSWER", reference, options));
  }

  @Test
  void validAnswersAreNormalized() {
    assertEquals("A,B", QuestionAnswerRules.validate("MULTIPLE", "a， b", options));
    assertEquals("FALSE", QuestionAnswerRules.validate("TRUE_FALSE", "false", List.of()));
  }

  @Test
  void duplicateOptionKeysAreRejected() {
    assertThrows(
        BusinessException.class,
        () -> QuestionAnswerRules.validate("SINGLE", "A", List.of(options.get(0), options.get(0))));
  }
}
