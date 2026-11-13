package com.aiquestion.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.aiquestion.common.BusinessException;

class SurveyQuestionDraftTest {
  @Test void createsSupportedDraftsWithServerIds() {
    var service = new TrainingService(null, null, null, null, null);
    var ids = new java.util.HashSet<String>();
    for (String type : java.util.List.of("RATING", "SINGLE", "TEXT")) {
      var q = service.createQuestionDraft(type);
      assertTrue(ids.add(q.id()));
      assertTrue(q.id().matches("[a-zA-Z0-9_-]{1,80}"));
      assertEquals(type, q.type());
      assertTrue(q.required());
      assertEquals("", q.title());
      assertEquals(type.equals("SINGLE") ? 2 : 0, q.options().size());
    }
  }
  @Test void rejectsUnsupportedType() {
    var service = new TrainingService(null, null, null, null, null);
    assertThrows(BusinessException.class, () -> service.createQuestionDraft("BAD"));
    assertThrows(BusinessException.class, () -> service.createQuestionDraft(null));
  }
}
