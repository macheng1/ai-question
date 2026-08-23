package com.aiquestion.domain;

import java.math.BigDecimal;
import java.util.List;

/** 题目基础模型。选择题选项通过 question_option 表维护。 */
public class QuestionItem {
    private Long id; private Long bankId; private String questionType; private String stem; private String answerText;
    private String analysis; private String tags; private String source; private String difficulty; private BigDecimal score; private Integer sortNo; private String status; private List<QuestionOption> options;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getBankId(){return bankId;} public void setBankId(Long v){bankId=v;}
    public String getQuestionType(){return questionType;} public void setQuestionType(String v){questionType=v;}
    public String getStem(){return stem;} public void setStem(String v){stem=v;}
    public String getAnswerText(){return answerText;} public void setAnswerText(String v){answerText=v;}
    public String getAnalysis(){return analysis;} public void setAnalysis(String v){analysis=v;}
    public String getTags(){return tags;} public void setTags(String v){tags=v;}
    public String getSource(){return source;} public void setSource(String v){source=v;}
    public String getDifficulty(){return difficulty;} public void setDifficulty(String v){difficulty=v;}
    public BigDecimal getScore(){return score;} public void setScore(BigDecimal v){score=v;}
    public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public List<QuestionOption> getOptions(){return options;} public void setOptions(List<QuestionOption> v){options=v;}
}
