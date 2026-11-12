package com.aiquestion.repository;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TrainingAudienceService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TrainingAudienceRepository {
  List<String> findAudienceType(@Param("trainingId") Long trainingId);
  List<Long> findScopeTargetIds(@Param("trainingId") Long trainingId);
  List<Integer> countOwnedTrainings(@Param("trainingId") Long trainingId, @Param("ownerId") Long ownerId);
  List<Map<String, Object>> lockTraining(@Param("trainingId") Long trainingId);
  List<Integer> countLearningSessions(@Param("trainingId") Long trainingId);
  List<Integer> countExamParticipants(@Param("assessmentId") Long assessmentId);
  List<Map<String, Object>> findActiveEmployees(@Param("ownerId") Long ownerId);
  List<Long> findActiveDepartmentIds(@Param("ownerId") Long ownerId);
  List<Map<String, Object>> findAssignments(@Param("trainingId") Long trainingId);
  int deleteAudience(@Param("trainingId") Long trainingId);
  int deleteScopeTargets(@Param("trainingId") Long trainingId);
  int updateAudienceType(@Param("audienceType") String audienceType, @Param("trainingId") Long trainingId);
  int insertScopeTarget(@Param("trainingId") Long trainingId, @Param("targetId") Long targetId);
  int insertAssignment(
      @Param("trainingId") Long trainingId,
      @Param("employeeId") Long employeeId,
      @Param("name") String name,
      @Param("phone") String phone,
      @Param("departmentId") Long departmentId,
      @Param("departmentName") String departmentName,
      @Param("assignedAt") java.sql.Timestamp assignedAt);
  List<Integer> countCandidateAssignments(@Param("trainingId") Long trainingId, @Param("name") String name, @Param("phone") String phone);
  List<Integer> countNotStartedTrainings(@Param("entryId") Long entryId, @Param("examTask") boolean examTask);
  List<Integer> countExamLinkedTrainings(@Param("trainingId") Long trainingId);
  List<Long> findTrainingIdsForEntry(@Param("entryId") Long entryId, @Param("examTask") boolean examTask);
}
