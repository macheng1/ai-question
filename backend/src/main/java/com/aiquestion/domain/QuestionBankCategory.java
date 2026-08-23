package com.aiquestion.domain;

/** 题库分类，parentId 为空时表示根分类。 */
public class QuestionBankCategory {
    private Long id;
    private String name;
    private Long parentId;
    private String description;
    private Integer sortNo;
    private Long createdBy;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public String getName(){return name;} public void setName(String name){this.name=name;}
    public Long getParentId(){return parentId;} public void setParentId(Long parentId){this.parentId=parentId;}
    public String getDescription(){return description;} public void setDescription(String description){this.description=description;}
      public Integer getSortNo(){return sortNo;} public void setSortNo(Integer sortNo){this.sortNo=sortNo;}
      public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long createdBy){this.createdBy=createdBy;}
}
