package com.aiquestion.domain;

import java.math.BigDecimal;

/** 试卷模型，题目明细通过 test_paper_question 关联。 */
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class TestPaper {
  private Long id;
  private String name;
  private String description;
  private String mode;
  private BigDecimal totalScore;
  private BigDecimal passScore;
  private Integer durationMinutes;
  private String status;
  private int questionCount;
  private boolean hasAssessment;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String v) {
    mode = v;
  }

  public BigDecimal getTotalScore() {
    return totalScore;
  }

  public void setTotalScore(BigDecimal v) {
    totalScore = v;
  }

  public BigDecimal getPassScore() {
    return passScore;
  }

  public void setPassScore(BigDecimal v) {
    passScore = v;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(Integer v) {
    durationMinutes = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public int getQuestionCount() {
    return questionCount;
  }

  public void setQuestionCount(int v) {
    questionCount = v;
  }

  public boolean isHasAssessment() {
    return hasAssessment;
  }

  public void setHasAssessment(boolean v) {
    hasAssessment = v;
  }
}
