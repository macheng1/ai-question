package com.aiquestion.repository;

import java.util.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TrainingEligibilityService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TrainingEligibilityRepository {
  int lockAllocationOwner(@Param("ownerId") long ownerId);
  List<Map<String, Object>> findOwnedTaskPaper(@Param("taskId") Long taskId, @Param("ownerId") Long ownerId);
  List<Long> findOriginalEmployees(@Param("ownerId") Long ownerId, @Param("paperId") Long paperId, @Param("trainingId") Long trainingId);
  List<Long> findBoundEmployees(@Param("ownerId") Long ownerId, @Param("paperId") Long paperId, @Param("trainingId") Long trainingId);
  int insertBinding(@Param("ownerId") long ownerId, @Param("employeeId") long employeeId, @Param("paperId") long paperId, @Param("trainingId") long trainingId);
  List<Integer> ownsBinding(@Param("ownerId") long ownerId, @Param("employeeId") long employeeId, @Param("paperId") long paperId, @Param("trainingId") long trainingId);
  List<Long> findOwnedTraining(@Param("trainingId") long trainingId, @Param("ownerId") long ownerId);
  List<Long> findSubmittedEmployees(@Param("ownerId") Long ownerId, @Param("paperId") Long paperId);
}
