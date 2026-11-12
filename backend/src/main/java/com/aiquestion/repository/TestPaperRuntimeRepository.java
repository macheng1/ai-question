package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TestPaperService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TestPaperRuntimeRepository {
  int copyRandomBankConfig(@Param("targetPaperId") Long targetPaperId, @Param("sourcePaperId") Long sourcePaperId);
  List<Map<String, Object>> findRandomConfig(@Param("paperId") Long paperId);
  List<Integer> countBank(@Param("bankId") Long bankId);
  List<Integer> countRandomBank(@Param("paperId") Long paperId, @Param("bankId") Long bankId);
  int insertRandomBank(@Param("paperId") Long paperId, @Param("bankId") Long bankId);
  int deleteRandomBank(@Param("paperId") Long paperId, @Param("bankId") Long bankId);
  int deleteRandomConfig(@Param("paperId") Long paperId);
  int insertRandomConfig(@Param("paperId") Long paperId, @Param("bankId") Long bankId, @Param("singleCount") int singleCount, @Param("singleScore") java.math.BigDecimal singleScore, @Param("multipleCount") int multipleCount, @Param("multipleScore") java.math.BigDecimal multipleScore, @Param("trueFalseCount") int trueFalseCount, @Param("trueFalseScore") java.math.BigDecimal trueFalseScore);
  List<BigDecimal> sumRandomScores(@Param("paperId") Long paperId);
  List<Integer> sumRandomQuestionCount(@Param("paperId") Long paperId);
  List<Integer> countConfiguredBank(@Param("bankId") Long bankId);
  List<Integer> countAccessibleBank(@Param("bankId") Long bankId, @Param("ownerId") Long ownerId);
  List<Integer> countAvailableQuestions(@Param("bankId") Long bankId, @Param("questionType") String questionType);
}
