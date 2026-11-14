package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for AssessmentRosterService. SQL is defined in the matching mapper XML. */
@Mapper
public interface AssessmentRosterRepository {
  List<Long> shareTask(@Param("taskId") Long taskId);
  List<Long> shareTrainings(@Param("taskId") Long taskId);
  int lockCandidate(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone);
  List<Long> lockTask(@Param("taskId") Long taskId);
  List<Integer> countTrainingReferences(@Param("taskId") Long taskId);
  List<Map<String, Object>> findTrainingRoster(@Param("taskId") Long taskId);
  List<Integer> countTask(@Param("taskId") Long taskId);
}
