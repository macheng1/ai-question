package com.aiquestion.repository;

import com.aiquestion.domain.QuestionBank;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import com.aiquestion.common.PageQuery;
import org.apache.ibatis.annotations.Param;

/** MyBatis Mapper 只负责 SQL 映射，不承载业务逻辑。 */
@Mapper
public interface QuestionBankRepository {
    List<QuestionBank> findPage(PageQuery query);
    long count(PageQuery query);
    List<QuestionBank> findOwnedPage(@Param("query") PageQuery query, @Param("userId") Long userId);
    long countOwned(@Param("query") PageQuery query, @Param("userId") Long userId);
    List<QuestionBank> findPublished(PageQuery query);
    long countPublished(PageQuery query);
    QuestionBank findPublishedById(Long id);
    QuestionBank findById(Long id);
    QuestionBank findOwnedById(@Param("id") Long id, @Param("userId") Long userId);
    int insert(QuestionBank bank);
    int updateOwned(@Param("bank") QuestionBank bank, @Param("userId") Long userId);
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);
    int incrementQuestionCount(Long id);
    int decrementQuestionCount(Long id);
}
