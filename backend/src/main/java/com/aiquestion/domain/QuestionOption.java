package com.aiquestion.domain;

public class QuestionOption {
    private Long id; private Long questionId; private String optionKey; private String optionText; private boolean correct; private Integer sortNo;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getQuestionId(){return questionId;} public void setQuestionId(Long v){questionId=v;} public String getOptionKey(){return optionKey;} public void setOptionKey(String v){optionKey=v;} public String getOptionText(){return optionText;} public void setOptionText(String v){optionText=v;} public boolean isCorrect(){return correct;} public void setCorrect(boolean v){correct=v;} public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;}
}
