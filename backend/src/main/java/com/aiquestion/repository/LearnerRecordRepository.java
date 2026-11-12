package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for LearnerRecordService. SQL is defined in the matching mapper XML. */
@Mapper
public interface LearnerRecordRepository {
  List<Map<String, Object>> findAssignedTrainings(@Param("employeeId") Long employeeId, @Param("enterpriseId") Long enterpriseId);
  List<Map<String, Object>> findTrainingMaterials(@Param("videoSessionId") Long videoSessionId, @Param("trainingId") Long trainingId, @Param("materialSessionId") Long materialSessionId, @Param("materialTrainingId") Long materialTrainingId);
  List<Map<String, Object>> findExamRecords(@Param("enterpriseId") Long enterpriseId, @Param("employeeId") Long employeeId);
  List<Map<String, Object>> findLatestMistakes(@Param("enterpriseId") Long enterpriseId, @Param("employeeId") Long employeeId);
  List<Map<String, Object>> findQuestionOptions(@Param("questionIds") List<?> questionIds);
}
