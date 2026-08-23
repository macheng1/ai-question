package com.aiquestion.common;

import java.util.List;

/** 分页响应对象，前端表格和移动端列表统一使用。 */
public record PageResult<T>(List<T> records, long total, int pageNo, int pageSize, int pages) {
    public static <T> PageResult<T> of(List<T> records,long total,PageQuery query){int pages=(int)Math.ceil((double)total/query.getPageSize());return new PageResult<>(records,total,query.getPageNo(),query.getPageSize(),pages);}
}
