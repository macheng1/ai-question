package com.aiquestion.repository;

import com.aiquestion.common.PageQuery;
import com.aiquestion.domain.QuestionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuestionItemRepository {
    List<QuestionItem> findPage(@Param("bankId") Long bankId, @Param("query") PageQuery query);
    long count(@Param("bankId") Long bankId, @Param("query") PageQuery query);
    List<QuestionItem> findPublishedPage(@Param("bankId") Long bankId, @Param("query") PageQuery query);
    long countPublished(@Param("bankId") Long bankId, @Param("query") PageQuery query);
    QuestionItem findById(Long id);
    int insert(QuestionItem item);
    int update(QuestionItem item);
    int deleteById(Long id);
    long countByBankIdAndStem(@Param("bankId") Long bankId, @Param("stem") String stem);
}
