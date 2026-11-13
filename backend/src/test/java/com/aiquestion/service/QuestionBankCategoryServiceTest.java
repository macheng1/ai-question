package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aiquestion.common.BusinessException;
import com.aiquestion.domain.QuestionBankCategory;
import com.aiquestion.repository.QuestionBankCategoryRepository;
import org.junit.jupiter.api.Test;

class QuestionBankCategoryServiceTest {
  private final QuestionBankCategoryRepository repository = mock(QuestionBankCategoryRepository.class);
  private final QuestionBankCategoryService service = new QuestionBankCategoryService(repository);

  @Test
  void rejectsCreatingChildCategory() {
    assertThrows(BusinessException.class, () -> service.create("子分类", 1L, "", 0, 5L));
    verify(repository, never()).insert(any());
  }

  @Test
  void rejectsMovingCategoryUnderAnotherCategory() {
    when(repository.findOwnedById(2L, 5L)).thenReturn(new QuestionBankCategory());
    assertThrows(BusinessException.class, () -> service.update(2L, "分类", 1L, "", 0, 5L));
    verify(repository, never()).update(any());
  }

  @Test
  void allowsCreatingRootCategory() {
    service.create("分类", null, "", 0, 5L);
    verify(repository).insert(argThat(category -> category.getParentId() == null && category.getName().equals("分类")));
  }
}
