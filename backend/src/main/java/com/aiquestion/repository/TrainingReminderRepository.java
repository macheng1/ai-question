package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TrainingReminderService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TrainingReminderRepository {
  List<Map<String, Object>> findTrainingDeadline(@Param("trainingId") Long trainingId);
  List<Map<String, Object>> findParticipantsWithoutExamReminder(@Param("taskId") Long taskId);
  List<Map<String, Object>> findReminderCandidates(@Param("trainingId") Long trainingId, @Param("ownerId") Long ownerId);
  int insertDelivery(@Param("trainingId") Long trainingId, @Param("employeeId") Long employeeId, @Param("requestId") String requestId, @Param("email") String email);
  int updateDelivery(@Param("status") String status, @Param("message") String message, @Param("sentStatus") String sentStatus, @Param("trainingId") Long trainingId, @Param("employeeId") Long employeeId, @Param("requestId") String requestId);
}
