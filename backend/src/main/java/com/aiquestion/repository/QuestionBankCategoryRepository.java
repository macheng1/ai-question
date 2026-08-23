package com.aiquestion.repository;

import com.aiquestion.domain.QuestionBankCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuestionBankCategoryRepository {
    List<QuestionBankCategory> findAll();
    List<QuestionBankCategory> findOwned(@Param("userId") Long userId);
    List<QuestionBankCategory> findPublicRoots();
    QuestionBankCategory findById(Long id);
    QuestionBankCategory findOwnedById(@Param("id") Long id, @Param("userId") Long userId);
    int insert(QuestionBankCategory category);
    int update(QuestionBankCategory category);
    int deleteById(QuestionBankCategory category);
}
