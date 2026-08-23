package com.aiquestion.repository;

import com.aiquestion.domain.QuestionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface TestPaperQuestionRepository {
    List<QuestionItem> findQuestions(@Param("paperId") Long paperId);
    int count(@Param("paperId") Long paperId);
    BigDecimal sumScore(@Param("paperId") Long paperId);
    int insert(@Param("paperId") Long paperId, @Param("questionId") Long questionId, @Param("score") BigDecimal score, @Param("sortNo") Integer sortNo);
    int delete(@Param("paperId") Long paperId, @Param("questionId") Long questionId);
    int updateScore(@Param("paperId") Long paperId, @Param("questionId") Long questionId, @Param("score") BigDecimal score);
}
