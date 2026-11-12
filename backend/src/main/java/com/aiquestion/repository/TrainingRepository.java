package com.aiquestion.repository;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TrainingService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TrainingRepository {
  List<Map<String, Object>> findById(@Param("trainingId") Long trainingId, @Param("lock") boolean lock);
  List<Long> countSurveyResponses(@Param("trainingId") Long trainingId);
  List<Map<String, Object>> findByOwner(@Param("ownerId") Long ownerId);
  List<Integer> countExamParticipants(@Param("assessmentId") Long assessmentId);
  List<Integer> countTrainingLearningSessions(@Param("trainingId") Long trainingId);
  List<Integer> countOwnedAssessments(@Param("assessmentId") Long assessmentId, @Param("ownerId") Long ownerId);
  List<Integer> countOtherTrainingReferences(@Param("assessmentId") Long assessmentId, @Param("excludedTrainingId") Long excludedTrainingId);
  List<Long> countAssessmentActivity(@Param("assessmentId") Long assessmentId, @Param("sessionAssessmentId") Long sessionAssessmentId);
  int insertTraining(
      @Param("name") String name,
      @Param("description") String description,
      @Param("surveyEnabled") boolean surveyEnabled,
      @Param("questionsJson") String questionsJson,
      @Param("ownerId") Long ownerId,
      @Param("generated") GeneratedId generated);
  List<Integer> countLearningBeforeRebind(@Param("trainingId") Long trainingId, @Param("assessmentId") Long assessmentId);
  List<Integer> countParticipantsBeforeRebind(@Param("assessmentId") Long assessmentId);
  int updateContent(
      @Param("name") String name,
      @Param("description") String description,
      @Param("surveyEnabled") boolean surveyEnabled,
      @Param("questionsJson") String questionsJson,
      @Param("trainingId") Long trainingId);
  int updateSettings(
      @Param("assessmentId") Long assessmentId,
      @Param("category") String category,
      @Param("requirementType") String requirementType,
      @Param("purpose") String purpose,
      @Param("endAt") java.time.LocalDateTime endAt,
      @Param("startAt") java.time.LocalDateTime startAt,
      @Param("trainingId") Long trainingId);
  int clearCopiedAssessment(@Param("trainingId") Long trainingId);
  List<Integer> countUpcomingTrainings(@Param("trainingId") Long trainingId);
  List<Integer> countParticipantsBeforeDelete(@Param("assessmentId") Long assessmentId);
  List<Integer> countSessionsBeforeDelete(@Param("trainingId") Long trainingId);
  List<Integer> countMaterials(@Param("trainingId") Long trainingId);
  int deleteTraining(@Param("trainingId") Long trainingId);
  List<Integer> countAssessmentReferences(@Param("assessmentId") Long assessmentId);
  List<Integer> countCopiedExamParticipants(@Param("assessmentId") Long assessmentId);
  List<Integer> countCopiedExamSessions(@Param("assessmentId") Long assessmentId);
  int deleteOwnedAssessment(@Param("assessmentId") Long assessmentId, @Param("ownerId") Long ownerId);
  List<Map<String, Object>> findAdministratorIdentity(@Param("userId") Long userId);
  List<Integer> countSubmittedSurvey(
      @Param("trainingId") Long trainingId,
      @Param("accountId") Long accountId,
      @Param("employeeIdentity") boolean employeeIdentity);
  int insertSurveyResponse(
      @Param("trainingId") Long trainingId,
      @Param("accountId") Long accountId,
      @Param("answersJson") String answersJson,
      @Param("employeeIdentity") boolean employeeIdentity);
  List<Map<String, Object>> findSurveyResponses(@Param("trainingId") Long trainingId);
  List<Map<String, Object>> findAdministratorVideoProgress(@Param("userId") Long userId, @Param("trainingId") Long trainingId);
  int saveAdministratorVideoProgress(
      @Param("videoId") Long videoId,
      @Param("userId") Long userId,
      @Param("position") double position,
      @Param("completed") boolean completed);
  List<Map<String, Object>> findVideo(@Param("trainingId") Long trainingId, @Param("videoId") Long videoId);
  List<Integer> countSessionsBeforeAddingVideo(@Param("trainingId") Long trainingId);
  int insertVideo(
      @Param("trainingId") Long trainingId,
      @Param("title") String title,
      @Param("storagePath") String storagePath,
      @Param("contentType") String contentType);
  List<Integer> countCandidateVideoProgress(@Param("videoId") Long videoId);
  List<Integer> countAdministratorVideoProgress(@Param("videoId") Long videoId);
  int deleteVideo(@Param("videoId") Long videoId, @Param("trainingId") Long trainingId);
  List<Long> findVideoIds(@Param("trainingId") Long trainingId);
  int updateVideoOrder(@Param("sortOrder") int sortOrder, @Param("trainingId") Long trainingId, @Param("videoId") Long videoId);
  List<String> findVideoStoragePaths(@Param("trainingId") Long trainingId);
}
