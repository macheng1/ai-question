package com.aiquestion.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 通用分页参数，页码从 1 开始，默认每页 10 条。 */
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class PageQuery {
  @Min(1)
  private int pageNo = 1;

  @Min(1)
  @Max(100)
  private int pageSize = 10;

  private String keyword;

  public int getPageNo() {
    return pageNo;
  }

  public void setPageNo(int pageNo) {
    this.pageNo = pageNo;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }

  public long getOffset() {
    return (long) (pageNo - 1) * pageSize;
  }
}
