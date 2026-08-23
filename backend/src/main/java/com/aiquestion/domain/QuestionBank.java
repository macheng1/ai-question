package com.aiquestion.domain;

/** 题库查询模型，由 MyBatis XML 映射数据库字段。 */
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class QuestionBank {
  private Long id;
  private String name;
  private int questionCount;
  private String category;
  private String source;
  private boolean published;
  private Long createdBy;

  public QuestionBank() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getQuestionCount() {
    return questionCount;
  }

  public void setQuestionCount(int questionCount) {
    this.questionCount = questionCount;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public boolean isPublished() {
    return published;
  }

  public void setPublished(boolean published) {
    this.published = published;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long createdBy) {
    this.createdBy = createdBy;
  }
}
