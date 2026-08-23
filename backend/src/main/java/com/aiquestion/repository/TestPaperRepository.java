package com.aiquestion.repository;

import com.aiquestion.common.PageQuery;
import com.aiquestion.domain.TestPaper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface TestPaperRepository {
    List<TestPaper> findPage(PageQuery query); long count(PageQuery query); TestPaper findById(Long id);
    int insert(TestPaper paper); int update(TestPaper paper); int updateStatus(Long id, String status); int deleteById(Long id);
}
