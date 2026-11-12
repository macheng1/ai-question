package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.domain.QuestionItem;
import com.aiquestion.domain.TestPaper;
import com.aiquestion.repository.QuestionItemRepository;
import com.aiquestion.repository.QuestionOptionRepository;
import com.aiquestion.repository.TestPaperRepository;
import com.aiquestion.repository.TestPaperQuestionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import com.aiquestion.repository.TestPaperRuntimeRepository;

@Service
public class /**
 * 后端业务组件。
 *
 * @author aiquestion
 */
TestPaperService {

    private final TestPaperRepository repository;

    private final TestPaperQuestionRepository questionRepository;

    private final QuestionItemRepository itemRepository;

    private final QuestionOptionRepository optionRepository;

    private final TestPaperRuntimeRepository persistence;

    public TestPaperService(TestPaperRepository repository, TestPaperQuestionRepository questionRepository, QuestionItemRepository itemRepository, QuestionOptionRepository optionRepository, TestPaperRuntimeRepository persistence) {
        this.repository = repository;
        this.questionRepository = questionRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.persistence = persistence;
    }

    public PageResult<TestPaper> page(PageQuery query) {
        return PageResult.of(repository.findPage(query), repository.count(query), query);
    }

    public TestPaper get(Long id) {
        var paper = repository.findById(id);
        if (paper == null || !ManagementAccess.userId().equals(paper.getCreatedBy()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "试卷不存在");
        return paper;
    }

    public TestPaper create(String name, String description, String mode, BigDecimal total, BigDecimal pass, Integer duration) {
        var p = new TestPaper();
        p.setCreatedBy(ManagementAccess.userId());
        p.setName(name);
        p.setDescription(description);
        p.setMode(mode);
        p.setTotalScore(total == null ? BigDecimal.ZERO : total);
        p.setPassScore(pass == null ? BigDecimal.valueOf(60) : pass);
        p.setDurationMinutes(duration == null ? 30 : duration);
        p.setStatus("DRAFT");
        repository.insert(p);
        return get(p.getId());
    }

    public TestPaper update(Long id, String name, String description, String mode, BigDecimal total, BigDecimal pass, Integer duration) {
        var p = get(id);
        ensureEditable(p);
        if ("PUBLISHED".equals(p.getStatus()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布试卷不能直接修改");
        p.setName(name);
        p.setDescription(description);
        p.setMode(mode);
        p.setTotalScore(total);
        p.setPassScore(pass);
        p.setDurationMinutes(duration);
        repository.update(p);
        return get(id);
    }

    public TestPaper changeStatus(Long id, String status) {
        get(id);
        if (!status.equals("DRAFT") && !status.equals("PUBLISHED") && !status.equals("DISABLED"))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷状态不合法");
        if ("PUBLISHED".equals(status))
            ensureScoreMatched(id);
        repository.updateStatus(id, status);
        return get(id);
    }

    public void delete(Long id) {
        var paper = get(id);
        if (paper.isHasAssessment()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷已有考试任务，不能删除");
        }
        try {
            if (repository.deleteById(id) == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "试卷不存在");
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷已被关联，不能删除");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public TestPaper copy(Long id, String name, String description) {
        var source = get(id);
        var target = new TestPaper();
        target.setCreatedBy(ManagementAccess.userId());
        target.setName(name);
        target.setDescription(description == null ? source.getDescription() : description);
        target.setMode(source.getMode());
        target.setTotalScore(source.getTotalScore());
        target.setPassScore(source.getPassScore());
        target.setDurationMinutes(source.getDurationMinutes());
        target.setStatus("DRAFT");
        repository.insert(target);
        if ("RANDOM".equalsIgnoreCase(source.getMode())) {
            for (var row : randomConfig(id)) checkBank(((Number) row.get("bankId")).longValue());
            // 随机试卷没有固定题目关联，复制时必须保留每个题库的抽题数量和分值规则。
            persistence.copyRandomBankConfig(target.getId(), id);
        } else {
            for (var question : questionRepository.findQuestions(id)) {
                checkQuestion(question.getId());
                questionRepository.insert(target.getId(), question.getId(), question.getScore(), question.getSortNo());
            }
        }
        return get(target.getId());
    }

    public List<QuestionItem> questions(Long id) {
        get(id);
        var items = questionRepository.findQuestions(id);
        items.forEach(item -> checkBank(item.getBankId()));
        return withOptions(items);
    }

    public QuestionItem addQuestion(Long paperId, Long questionId, BigDecimal score) {
        ensureEditable(get(paperId));
        if (itemRepository.findById(questionId) == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        checkQuestion(questionId);
        questionRepository.insert(paperId, questionId, score == null ? BigDecimal.ONE : score, questionRepository.count(paperId));
        return withOptions(itemRepository.findById(questionId));
    }

    public void removeQuestion(Long paperId, Long questionId) {
        ensureEditable(get(paperId));
        if (questionRepository.delete(paperId, questionId) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "试卷中不存在该题目");
    }

    public QuestionItem updateQuestionScore(Long paperId, Long questionId, BigDecimal score) {
        ensureEditable(get(paperId));
        if (score == null || score.signum() <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "题目分值必须大于 0");
        if (questionRepository.updateScore(paperId, questionId, score) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "试卷中不存在该题目");
        return withOptions(questionRepository.findQuestions(paperId).stream().filter(item -> questionId.equals(item.getId())).findFirst().orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在")));
    }

    private List<QuestionItem> withOptions(List<QuestionItem> items) {
        items.forEach(this::withOptions);
        return items;
    }

    private QuestionItem withOptions(QuestionItem item) {
        if (item != null)
            item.setOptions(optionRepository.findByQuestionId(item.getId()));
        return item;
    }

    public List<Map<String, Object>> randomConfig(Long paperId) {
        TestPaper paper = get(paperId);
        ensureRandom(paper);
        var rows = persistence.findRandomConfig(paperId);
        rows.forEach(row -> checkBank(((Number) row.get("bankId")).longValue()));
        return rows;
    }

    public void addRandomBank(Long paperId, Long bankId) {
        TestPaper paper = get(paperId);
        ensureRandom(paper);
        ensureEditable(paper);
        checkBank(bankId);
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countBank(bankId)) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "题库不存在");
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countRandomBank(paperId, bankId)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该题库已经添加");
        persistence.insertRandomBank(paperId, bankId);
    }

    public void removeRandomBank(Long paperId, Long bankId) {
        TestPaper paper = get(paperId);
        ensureRandom(paper);
        ensureEditable(paper);
        if (persistence.deleteRandomBank(paperId, bankId) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "随机组卷题库不存在");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveRandomConfig(Long paperId, List<RandomConfigItem> items) {
        TestPaper paper = get(paperId);
        ensureRandom(paper);
        ensureEditable(paper);
        if (items == null)
            items = List.of();
        for (RandomConfigItem item : items) validateRandomItem(paperId, item);
        persistence.deleteRandomConfig(paperId);
        for (RandomConfigItem item : items) {
            persistence.insertRandomConfig(paperId, item.bankId(), count(item.singleCount()), score(item.singleScore()), count(item.multipleCount()), score(item.multipleScore()), count(item.trueFalseCount()), score(item.trueFalseScore()));
        }
    }

    public BigDecimal randomTotal(Long paperId) {
        return com.aiquestion.repository.RepositoryRows.one(persistence.sumRandomScores(paperId));
    }

    public void validateRandomScore(Long paperId) {
        TestPaper paper = get(paperId);
        ensureRandom(paper);
        BigDecimal total = randomTotal(paperId);
        Integer count = com.aiquestion.repository.RepositoryRows.one(persistence.sumRandomQuestionCount(paperId));
        if (count == null || count == 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少配置一道随机题目");
        if (total.compareTo(paper.getTotalScore()) != 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "随机题目总分为 " + total.stripTrailingZeros().toPlainString() + " 分，必须等于试卷设置的 " + paper.getTotalScore().stripTrailingZeros().toPlainString() + " 分");
    }

    private void validateRandomItem(Long paperId, RandomConfigItem item) {
        if (item == null || item.bankId() == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "随机题库不能为空");
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countConfiguredBank(item.bankId())) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND, "题库不存在");
        checkBank(item.bankId());
        int single = count(item.singleCount()), multiple = count(item.multipleCount()), judge = count(item.trueFalseCount());
        if (single < 0 || multiple < 0 || judge < 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "抽题数量不能小于 0");
        int availableSingle = available(item.bankId(), "SINGLE"), availableMultiple = available(item.bankId(), "MULTIPLE"), availableJudge = available(item.bankId(), "TRUE_FALSE");
        if (single > availableSingle || multiple > availableMultiple || judge > availableJudge)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "题库可用题目数量不足，请减少抽题数量");
        boolean singleScoreInvalid = single > 0 && score(item.singleScore()).signum() <= 0;
        boolean multipleScoreInvalid = multiple > 0 && score(item.multipleScore()).signum() <= 0;
        boolean judgeScoreInvalid = judge > 0 && score(item.trueFalseScore()).signum() <= 0;
        if (singleScoreInvalid || multipleScoreInvalid || judgeScoreInvalid) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已配置题目的分值必须大于 0");
        }
    }

    private void checkBank(Long id) {
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countAccessibleBank(id, ManagementAccess.userId())) != 1)
            throw new BusinessException(ErrorCode.NOT_FOUND, "题库不存在");
    }

    private void checkQuestion(Long id) {
        var item = itemRepository.findById(id);
        if (item == null || !"ENABLED".equals(item.getStatus()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在或已禁用");
        checkBank(item.getBankId());
    }

    private int available(Long bankId, String type) {
        return com.aiquestion.repository.RepositoryRows.one(persistence.countAvailableQuestions(bankId, type));
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal score(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void ensureRandom(TestPaper paper) {
        if (!"RANDOM".equalsIgnoreCase(paper.getMode()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该试卷不是随机组卷");
    }

    private void ensureEditable(TestPaper paper) {
        if (paper.isHasAssessment())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷已有考试任务，组卷已锁定");
    }

    private void ensureScoreMatched(Long paperId) {
        var paper = get(paperId);
        if ("RANDOM".equalsIgnoreCase(paper.getMode())) {
            validateRandomScore(paperId);
            return;
        }
        var actual = questionRepository.sumScore(paperId);
        if (actual == null)
            actual = BigDecimal.ZERO;
        if (actual.compareTo(paper.getTotalScore()) != 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "题目总分为 " + actual.stripTrailingZeros().toPlainString() + " 分，必须等于试卷设置的 " + paper.getTotalScore().stripTrailingZeros().toPlainString() + " 分");
    }

    public record RandomConfigItem(Long bankId, Integer singleCount, BigDecimal singleScore, Integer multipleCount, BigDecimal multipleScore, Integer trueFalseCount, BigDecimal trueFalseScore) {
    }
}
