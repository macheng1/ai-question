package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.controller.QuestionItemController.OptionRequest;
import com.aiquestion.domain.QuestionItem;
import com.aiquestion.repository.QuestionBankRepository;
import com.aiquestion.repository.QuestionItemRepository;
import com.aiquestion.repository.QuestionOptionRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class QuestionItemService {
  private final QuestionItemRepository repository;
  private final QuestionBankRepository bankRepository;
  private final QuestionOptionRepository optionRepository;

  public QuestionItemService(
      QuestionItemRepository repository,
      QuestionBankRepository bankRepository,
      QuestionOptionRepository optionRepository) {
    this.repository = repository;
    this.bankRepository = bankRepository;
    this.optionRepository = optionRepository;
  }
  /** 管理端题目列表也必须返回选项，供组卷页面展示和选择。 */
  public PageResult<QuestionItem> page(Long bankId, PageQuery query, Long userId) {
    if (bankId == null || bankRepository.findOwnedById(bankId, userId) == null) {
      throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
    }
    var records = repository.findPage(bankId, query);
    records.forEach(item -> item.setOptions(optionRepository.findByQuestionId(item.getId())));
    return PageResult.of(records, repository.count(bankId, query), query);
  }
  /** 查询公开题库题目，并补充选项，供题库浏览页展示答案和解析。 */
  public PageResult<QuestionItem> publicPage(Long bankId, PageQuery query) {
    if (bankRepository.findPublishedById(bankId) == null) {
      throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
    }
    var records = repository.findPublishedPage(bankId, query);
    records.forEach(item -> item.setOptions(optionRepository.findByQuestionId(item.getId())));
    return PageResult.of(records, repository.countPublished(bankId, query), query);
  }

  public QuestionItem get(Long id, Long userId) {
    var item = repository.findById(id);
    if (item == null || bankRepository.findOwnedById(item.getBankId(), userId) == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
    }
    item.setOptions(optionRepository.findByQuestionId(id));
    return item;
  }

  public QuestionItem create(
      Long bankId,
      String type,
      String stem,
      String answer,
      String analysis,
      String tags,
      String source,
      String difficulty,
      BigDecimal score,
      java.util.List<OptionRequest> options,
      Long userId) {
    if (bankRepository.findOwnedById(bankId, userId) == null) {
      throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
    }
    var item = new QuestionItem();
    item.setBankId(bankId);
    item.setQuestionType(type);
    item.setStem(stem);
    item.setAnswerText(answer);
    item.setAnalysis(analysis);
    item.setTags(tags);
    item.setSource(source);
    item.setDifficulty(difficulty);
    item.setScore(score);
    item.setStatus("ENABLED");
    repository.insert(item);
    saveOptions(item.getId(), options);
    bankRepository.incrementQuestionCount(bankId);
    return get(item.getId(), userId);
  }

  public QuestionItem update(
      Long id,
      String type,
      String stem,
      String answer,
      String analysis,
      String tags,
      String source,
      String difficulty,
      BigDecimal score,
      java.util.List<OptionRequest> options,
      Long userId) {
    var item = get(id, userId);
    item.setQuestionType(type);
    item.setStem(stem);
    item.setAnswerText(answer);
    item.setAnalysis(analysis);
    item.setTags(tags);
    item.setSource(source);
    item.setDifficulty(difficulty);
    item.setScore(score);
    repository.update(item);
    optionRepository.deleteByQuestionId(id);
    saveOptions(id, options);
    return get(id, userId);
  }

  public void delete(Long id, Long userId) {
    var item = get(id, userId);
    if (repository.deleteById(id) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
    }
    bankRepository.decrementQuestionCount(item.getBankId());
  }

  private void saveOptions(Long questionId, java.util.List<OptionRequest> options) {
    if (options == null) return;
    for (int i = 0; i < options.size(); i++) {
      var request = options.get(i);
      var option = new com.aiquestion.domain.QuestionOption();
      option.setQuestionId(questionId);
      option.setOptionKey(request.optionKey());
      option.setOptionText(request.optionText());
      option.setCorrect(Boolean.TRUE.equals(request.correct()));
      option.setSortNo(request.sortNo() == null ? i : request.sortNo());
      optionRepository.insert(option);
    }
  }
}
