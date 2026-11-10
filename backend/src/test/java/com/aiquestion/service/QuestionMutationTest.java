package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiquestion.common.BusinessException;
import com.aiquestion.domain.*;
import com.aiquestion.repository.*;
import org.junit.jupiter.api.Test;

class QuestionMutationTest {
  @Test
  void examinationPaperCannotBeDeleted() {
    var repo = mock(TestPaperRepository.class);
    var service = spy(new TestPaperService(repo, mock(TestPaperQuestionRepository.class),
        mock(QuestionItemRepository.class), mock(QuestionOptionRepository.class),
        mock(org.springframework.jdbc.core.JdbcTemplate.class)));
    var paper = new TestPaper();
    paper.setHasAssessment(true);
    doReturn(paper).when(service).get(1L);
    assertEquals("试卷已有考试任务，不能删除",
        assertThrows(BusinessException.class, () -> service.delete(1L)).getMessage());
    verify(repo, never()).deleteById(any());
  }

  @Test
  void referencedBankDeletionIsBusinessError() {
    var repo = mock(QuestionBankRepository.class);
    when(repo.deleteOwned(1L, 2L))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("foreign key"));
    assertEquals(
        "题库仍有题目或关联试卷，不能删除",
        assertThrows(BusinessException.class, () -> new QuestionBankService(repo).delete(1L, 2L))
            .getMessage());
  }

  @Test
  void examinationQuestionCannotBeChanged() {
    var repo = mock(QuestionItemRepository.class);
    var banks = mock(QuestionBankRepository.class);
    var options = mock(QuestionOptionRepository.class);
    var item = new QuestionItem();
    item.setId(1L);
    item.setBankId(3L);
    when(repo.findById(1L)).thenReturn(item);
    when(banks.findOwnedById(3L, 2L)).thenReturn(new QuestionBank());
    when(repo.countAssessmentReferences(1L)).thenReturn(1L);
    var service = new QuestionItemService(repo, banks, options);
    assertThrows(
        BusinessException.class,
        () ->
            service.update(
                1L,
                "SINGLE",
                "changed",
                "A",
                null,
                null,
                null,
                "MEDIUM",
                java.math.BigDecimal.ONE,
                java.util.List.of(),
                2L));
    verify(repo, never()).update(any());
    verify(options, never()).deleteByQuestionId(any());
  }
}
