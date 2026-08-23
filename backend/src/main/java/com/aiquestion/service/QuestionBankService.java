package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.common.PageQuery;
import com.aiquestion.common.PageResult;
import com.aiquestion.domain.QuestionBank;
import com.aiquestion.repository.QuestionBankRepository;
import org.springframework.stereotype.Service;

@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class QuestionBankService {
  private final QuestionBankRepository repository;

  public QuestionBankService(QuestionBankRepository repository) {
    this.repository = repository;
  }
  /** 只返回公开题库，隐藏未发布数据。 */
  public PageResult<QuestionBank> list(PageQuery query) {
    var records = repository.findPublished(query);
    return PageResult.of(records, repository.countPublished(query), query);
  }
  /** 公开详情只允许访问已发布题库，避免通过详情地址读取私有题库。 */
  public QuestionBank getPublished(Long id) {
    var bank = repository.findPublishedById(id);
    if (bank == null) {
      throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
    }
    return bank;
  }

  public PageResult<QuestionBank> page(PageQuery query, Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    return PageResult.of(
        repository.findOwnedPage(query, userId), repository.countOwned(query, userId), query);
  }

  public QuestionBank create(
      String name, String category, String source, Boolean published, Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    var bank = new QuestionBank();
    bank.setName(name);
    bank.setCategory(category);
    bank.setSource(source);
    bank.setPublished(Boolean.TRUE.equals(published));
    bank.setCreatedBy(userId);
    repository.insert(bank);
    return get(bank.getId());
  }

  public QuestionBank update(
      Long id, String name, String category, String source, Boolean published, Long userId) {
    var bank = owned(id, userId);
    bank.setName(name);
    bank.setCategory(category);
    bank.setSource(source);
    bank.setPublished(Boolean.TRUE.equals(published));
    repository.updateOwned(bank, userId);
    return owned(id, userId);
  }

  public void delete(Long id, Long userId) {
    if (userId == null || repository.deleteOwned(id, userId) == 0) {
      throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
    }
  }
  /** 统一处理题库不存在的场景，由全局异常处理器转换为 404。 */
  public QuestionBank get(Long id) {
    var bank = repository.findById(id);
    if (bank == null) {
      throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
    }
    return bank;
  }

  private QuestionBank owned(Long id, Long userId) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    var bank = repository.findOwnedById(id, userId);
    if (bank == null) {
      throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
    }
    return bank;
  }
}
