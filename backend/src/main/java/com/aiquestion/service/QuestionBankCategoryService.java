package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.domain.QuestionBankCategory;
import com.aiquestion.repository.QuestionBankCategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class QuestionBankCategoryService {
  private final QuestionBankCategoryRepository repository;

  public QuestionBankCategoryService(QuestionBankCategoryRepository repository) {
    this.repository = repository;
  }

  public List<QuestionBankCategory> list(Long userId) {
    return repository.findOwned(userId);
  }
  /** 游客端只展示至少存在一个公开题库的根分类，避免出现空分类。 */
  public List<QuestionBankCategory> listPublicRoots() {
    return repository.findPublicRoots();
  }

  public QuestionBankCategory create(
      String name, Long parentId, String description, Integer sortNo, Long userId) {
    validateParent(parentId, userId);
    var category = new QuestionBankCategory();
    category.setName(name);
    category.setParentId(parentId);
    category.setCreatedBy(userId);
    category.setDescription(description);
    category.setSortNo(sortNo == null ? 0 : sortNo);
    repository.insert(category);
    return repository.findOwnedById(category.getId(), userId);
  }

  public QuestionBankCategory update(
      Long id, String name, Long parentId, String description, Integer sortNo, Long userId) {
    get(id, userId);
    if (id.equals(parentId)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "分类不能设置自己为父分类");
    }
    validateParent(parentId, userId);
    var category = new QuestionBankCategory();
    category.setId(id);
    category.setName(name);
    category.setParentId(parentId);
    category.setCreatedBy(userId);
    category.setDescription(description);
    category.setSortNo(sortNo == null ? 0 : sortNo);
    repository.update(category);
    return get(id, userId);
  }

  public void delete(Long id, Long userId) {
    var category = get(id, userId);
    if (repository.deleteById(category) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
    }
  }

  private QuestionBankCategory get(Long id, Long userId) {
    var category = repository.findOwnedById(id, userId);
    if (category == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
    }
    return category;
  }

  private void validateParent(Long parentId, Long userId) {
    if (parentId != null && repository.findOwnedById(parentId, userId) == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "父分类不存在");
    }
  }
}
