package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TrainingLogService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TrainingLogRepository {
  List<Long> countTrainingUnits(@Param("videoTrainingId") Long videoTrainingId, @Param("materialTrainingId") Long materialTrainingId);
  List<Map<String, Object>> findCandidateCompletion(@Param("trainingId") Long trainingId);
  List<Map<String, Object>> findLearningSessions(@Param("trainingId") Long trainingId);
  List<Map<String, Object>> findVideoLogs(@Param("trainingId") Long trainingId, @Param("sessionTrainingId") Long sessionTrainingId);
  List<Map<String, Object>> findMaterialLogs(@Param("trainingId") Long trainingId, @Param("sessionTrainingId") Long sessionTrainingId);
  List<Map<String, Object>> findCandidateAttempts(@Param("taskId") Long taskId, @Param("phone") String phone, @Param("candidateName") String candidateName);
}
