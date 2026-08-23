package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.domain.QuestionItem;
import com.aiquestion.domain.TestPaper;
import com.aiquestion.repository.QuestionItemRepository;
import com.aiquestion.repository.QuestionOptionRepository;
import com.aiquestion.repository.TestPaperRepository;
import com.aiquestion.repository.TestPaperQuestionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class TestPaperService {
    private final TestPaperRepository repository; private final TestPaperQuestionRepository questionRepository; private final QuestionItemRepository itemRepository; private final QuestionOptionRepository optionRepository; private final JdbcTemplate jdbc;
    public TestPaperService(TestPaperRepository repository, TestPaperQuestionRepository questionRepository, QuestionItemRepository itemRepository, QuestionOptionRepository optionRepository, JdbcTemplate jdbc){this.repository=repository;this.questionRepository=questionRepository;this.itemRepository=itemRepository;this.optionRepository=optionRepository;this.jdbc=jdbc;}
    public PageResult<TestPaper> page(PageQuery query){return PageResult.of(repository.findPage(query),repository.count(query),query);}
    public TestPaper get(Long id){var paper=repository.findById(id);if(paper==null)throw new BusinessException(ErrorCode.NOT_FOUND,"试卷不存在");return paper;}
    public TestPaper create(String name,String description,String mode,BigDecimal total,BigDecimal pass,Integer duration){var p=new TestPaper();p.setName(name);p.setDescription(description);p.setMode(mode);p.setTotalScore(total==null?BigDecimal.ZERO:total);p.setPassScore(pass==null?BigDecimal.valueOf(60):pass);p.setDurationMinutes(duration==null?30:duration);p.setStatus("DRAFT");repository.insert(p);return get(p.getId());}
    public TestPaper update(Long id,String name,String description,String mode,BigDecimal total,BigDecimal pass,Integer duration){var p=get(id);ensureEditable(p);if("PUBLISHED".equals(p.getStatus()))throw new BusinessException(ErrorCode.BAD_REQUEST,"已发布试卷不能直接修改");p.setName(name);p.setDescription(description);p.setMode(mode);p.setTotalScore(total);p.setPassScore(pass);p.setDurationMinutes(duration);repository.update(p);return get(id);}
    public TestPaper changeStatus(Long id,String status){get(id);if(!status.equals("DRAFT")&&!status.equals("PUBLISHED")&&!status.equals("DISABLED"))throw new BusinessException(ErrorCode.BAD_REQUEST,"试卷状态不合法");if("PUBLISHED".equals(status))ensureScoreMatched(id);repository.updateStatus(id,status);return get(id);}
    public void delete(Long id){if(repository.deleteById(id)==0)throw new BusinessException(ErrorCode.NOT_FOUND,"试卷不存在");}
    @Transactional(rollbackFor = Exception.class)
    public TestPaper copy(Long id,String name,String description){
        var source=get(id);var target=new TestPaper();
        target.setName(name);target.setDescription(description==null?source.getDescription():description);
        target.setMode(source.getMode());target.setTotalScore(source.getTotalScore());
        target.setPassScore(source.getPassScore());target.setDurationMinutes(source.getDurationMinutes());
        target.setStatus("DRAFT");repository.insert(target);
        if("RANDOM".equalsIgnoreCase(source.getMode())){
            // 随机试卷没有固定题目关联，复制时必须保留每个题库的抽题数量和分值规则。
            jdbc.update("""
                INSERT INTO test_paper_random_bank(
                  paper_id,bank_id,single_count,single_score,multiple_count,multiple_score,true_false_count,true_false_score
                )
                SELECT ?,bank_id,single_count,single_score,multiple_count,multiple_score,true_false_count,true_false_score
                FROM test_paper_random_bank WHERE paper_id=?
                """,target.getId(),id);
        }else{
            for(var question:questionRepository.findQuestions(id)){
                questionRepository.insert(target.getId(),question.getId(),question.getScore(),question.getSortNo());
            }
        }
        return get(target.getId());
    }
    public List<QuestionItem> questions(Long id){get(id);return withOptions(questionRepository.findQuestions(id));}
    public QuestionItem addQuestion(Long paperId,Long questionId,BigDecimal score){ensureEditable(get(paperId));if(itemRepository.findById(questionId)==null)throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");questionRepository.insert(paperId,questionId,score==null?BigDecimal.ONE:score,questionRepository.count(paperId));return withOptions(itemRepository.findById(questionId));}
    public void removeQuestion(Long paperId,Long questionId){ensureEditable(get(paperId));if(questionRepository.delete(paperId,questionId)==0)throw new BusinessException(ErrorCode.NOT_FOUND,"试卷中不存在该题目");}
    public QuestionItem updateQuestionScore(Long paperId,Long questionId,BigDecimal score){ensureEditable(get(paperId));if(score==null||score.signum()<=0)throw new BusinessException(ErrorCode.BAD_REQUEST,"题目分值必须大于 0");if(questionRepository.updateScore(paperId,questionId,score)==0)throw new BusinessException(ErrorCode.NOT_FOUND,"试卷中不存在该题目");return withOptions(questionRepository.findQuestions(paperId).stream().filter(item->questionId.equals(item.getId())).findFirst().orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"题目不存在")));}
    private List<QuestionItem> withOptions(List<QuestionItem> items){items.forEach(this::withOptions);return items;}
    private QuestionItem withOptions(QuestionItem item){if(item!=null)item.setOptions(optionRepository.findByQuestionId(item.getId()));return item;}
    public List<Map<String,Object>> randomConfig(Long paperId){
        TestPaper paper=get(paperId); ensureRandom(paper);
        return jdbc.queryForList("""
            SELECT r.id,r.bank_id bankId,b.name bankName,
              r.single_count singleCount,r.single_score singleScore,
              r.multiple_count multipleCount,r.multiple_score multipleScore,
              r.true_false_count trueFalseCount,r.true_false_score trueFalseScore,
              (SELECT COUNT(*) FROM question_item q WHERE q.bank_id=r.bank_id AND q.question_type='SINGLE' AND q.status='ENABLED') availableSingleCount,
              (SELECT COUNT(*) FROM question_item q WHERE q.bank_id=r.bank_id AND q.question_type='MULTIPLE' AND q.status='ENABLED') availableMultipleCount,
              (SELECT COUNT(*) FROM question_item q WHERE q.bank_id=r.bank_id AND q.question_type='TRUE_FALSE' AND q.status='ENABLED') availableTrueFalseCount
            FROM test_paper_random_bank r JOIN question_bank b ON b.id=r.bank_id
            WHERE r.paper_id=? ORDER BY r.id
            """, paperId);
    }
    public void addRandomBank(Long paperId,Long bankId){
        TestPaper paper=get(paperId); ensureRandom(paper); ensureEditable(paper);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM question_bank WHERE id=?",Integer.class,bankId)==0) throw new BusinessException(ErrorCode.NOT_FOUND,"题库不存在");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM test_paper_random_bank WHERE paper_id=? AND bank_id=?",Integer.class,paperId,bankId)>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"该题库已经添加");
        jdbc.update("INSERT INTO test_paper_random_bank(paper_id,bank_id) VALUES(?,?)",paperId,bankId);
    }
    public void removeRandomBank(Long paperId,Long bankId){
        TestPaper paper=get(paperId); ensureRandom(paper); ensureEditable(paper);
        if(jdbc.update("DELETE FROM test_paper_random_bank WHERE paper_id=? AND bank_id=?",paperId,bankId)==0) throw new BusinessException(ErrorCode.NOT_FOUND,"随机组卷题库不存在");
    }
    @Transactional(rollbackFor = Exception.class)
    public void saveRandomConfig(Long paperId,List<RandomConfigItem> items){
        TestPaper paper=get(paperId); ensureRandom(paper); ensureEditable(paper);
        if(items==null) items=List.of();
        for(RandomConfigItem item:items) validateRandomItem(paperId,item);
        jdbc.update("DELETE FROM test_paper_random_bank WHERE paper_id=?",paperId);
        for(RandomConfigItem item:items){
            jdbc.update("INSERT INTO test_paper_random_bank(paper_id,bank_id,single_count,single_score,multiple_count,multiple_score,true_false_count,true_false_score) VALUES(?,?,?,?,?,?,?,?)",
                    paperId,item.bankId(),count(item.singleCount()),score(item.singleScore()),count(item.multipleCount()),score(item.multipleScore()),count(item.trueFalseCount()),score(item.trueFalseScore()));
        }
    }
    public BigDecimal randomTotal(Long paperId){
        return jdbc.queryForObject("SELECT COALESCE(SUM(single_count*single_score+multiple_count*multiple_score+true_false_count*true_false_score),0) FROM test_paper_random_bank WHERE paper_id=?",BigDecimal.class,paperId);
    }
    public void validateRandomScore(Long paperId){
        TestPaper paper=get(paperId); ensureRandom(paper);
        BigDecimal total=randomTotal(paperId);
        Integer count=jdbc.queryForObject("SELECT COALESCE(SUM(single_count+multiple_count+true_false_count),0) FROM test_paper_random_bank WHERE paper_id=?",Integer.class,paperId);
        if(count==null||count==0) throw new BusinessException(ErrorCode.BAD_REQUEST,"请至少配置一道随机题目");
        if(total.compareTo(paper.getTotalScore())!=0) throw new BusinessException(ErrorCode.BAD_REQUEST,"随机题目总分为 "+total.stripTrailingZeros().toPlainString()+" 分，必须等于试卷设置的 "+paper.getTotalScore().stripTrailingZeros().toPlainString()+" 分");
    }
    private void validateRandomItem(Long paperId,RandomConfigItem item){
        if(item==null||item.bankId()==null) throw new BusinessException(ErrorCode.BAD_REQUEST,"随机题库不能为空");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM question_bank WHERE id=?",Integer.class,item.bankId())==0) throw new BusinessException(ErrorCode.NOT_FOUND,"题库不存在");
        int single=count(item.singleCount()),multiple=count(item.multipleCount()),judge=count(item.trueFalseCount());
        if(single<0||multiple<0||judge<0) throw new BusinessException(ErrorCode.BAD_REQUEST,"抽题数量不能小于 0");
        int availableSingle=available(item.bankId(),"SINGLE"),availableMultiple=available(item.bankId(),"MULTIPLE"),availableJudge=available(item.bankId(),"TRUE_FALSE");
        if(single>availableSingle||multiple>availableMultiple||judge>availableJudge) throw new BusinessException(ErrorCode.BAD_REQUEST,"题库可用题目数量不足，请减少抽题数量");
        boolean singleScoreInvalid = single > 0 && score(item.singleScore()).signum() <= 0;
        boolean multipleScoreInvalid = multiple > 0 && score(item.multipleScore()).signum() <= 0;
        boolean judgeScoreInvalid = judge > 0 && score(item.trueFalseScore()).signum() <= 0;
        if (singleScoreInvalid || multipleScoreInvalid || judgeScoreInvalid) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已配置题目的分值必须大于 0");
        }
    }
    private int available(Long bankId,String type){return jdbc.queryForObject("SELECT COUNT(*) FROM question_item WHERE bank_id=? AND question_type=? AND status='ENABLED'",Integer.class,bankId,type);}
    private static int count(Integer value){return value==null?0:value;}
    private static BigDecimal score(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private static void ensureRandom(TestPaper paper){if(!"RANDOM".equalsIgnoreCase(paper.getMode()))throw new BusinessException(ErrorCode.BAD_REQUEST,"该试卷不是随机组卷");}
    private void ensureEditable(TestPaper paper){if(paper.isHasAssessment())throw new BusinessException(ErrorCode.BAD_REQUEST,"试卷已有考试任务，组卷已锁定");}
    private void ensureScoreMatched(Long paperId){var paper=get(paperId);if("RANDOM".equalsIgnoreCase(paper.getMode())){validateRandomScore(paperId);return;}var actual=questionRepository.sumScore(paperId);if(actual==null)actual=BigDecimal.ZERO;if(actual.compareTo(paper.getTotalScore())!=0)throw new BusinessException(ErrorCode.BAD_REQUEST,"题目总分为 "+actual.stripTrailingZeros().toPlainString()+" 分，必须等于试卷设置的 "+paper.getTotalScore().stripTrailingZeros().toPlainString()+" 分");}
    public record RandomConfigItem(Long bankId,Integer singleCount,BigDecimal singleScore,Integer multipleCount,BigDecimal multipleScore,Integer trueFalseCount,BigDecimal trueFalseScore){}
}
