package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for CandidateLearningService. SQL is defined in the matching mapper XML. */
@Mapper
public interface CandidateLearningRepository {
  List<Map<String, Object>> findCandidateTrainings(@Param("taskId") Long taskId, @Param("examTask") boolean examTask, @Param("candidateName") String candidateName, @Param("phone") String phone);
  List<Map<String, Object>> findTrainings(@Param("taskId") Long taskId, @Param("examTask") boolean examTask);
  List<Integer> countIncompleteVideos(@Param("taskId") Long taskId, @Param("sessionTaskId") Long sessionTaskId, @Param("candidateName") String candidateName, @Param("phone") String phone, @Param("examTask") boolean examTask);
  List<Integer> countIncompletePages(@Param("taskId") Long taskId, @Param("sessionTaskId") Long sessionTaskId, @Param("candidateName") String candidateName, @Param("phone") String phone, @Param("examTask") boolean examTask);
  int confirmSessionsCompleted(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone, @Param("examTask") boolean examTask);
  List<Map<String, Object>> lockExamTrainings(@Param("taskId") Long taskId);
  List<Map<String, Object>> lockStandaloneTraining(@Param("trainingId") Long trainingId);
  List<Map<String, Object>> findTask(@Param("taskId") Long taskId);
  int insertSessionIfAbsent(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone, @Param("token") String token, @Param("examTask") boolean examTask);
  int assignSessionEmployee(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone, @Param("examTask") boolean examTask);
  List<Map<String, Object>> findCandidateSession(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone, @Param("examTask") boolean examTask);
  int initializeVideoProgress(@Param("sessionId") Long sessionId, @Param("taskId") Long taskId, @Param("examTask") boolean examTask);
  List<Map<String, Object>> findSessionVideos(@Param("sessionId") Long sessionId, @Param("taskId") Long taskId, @Param("examTask") boolean examTask);
  List<Map<String, Object>> findAuthorizedVideo(@Param("taskId") Long taskId, @Param("sessionTaskId") Long sessionTaskId, @Param("token") String token, @Param("videoId") Long videoId, @Param("examTask") boolean examTask);
  List<Map<String, Object>> findSessionIdentity(@Param("token") String token, @Param("taskId") Long taskId, @Param("examTask") boolean examTask);
  List<Map<String, Object>> lockVideoProgress(@Param("sessionId") Long sessionId, @Param("videoId") Long videoId);
  int updateVideoProgress(@Param("positionSeconds") double positionSeconds, @Param("completed") boolean completed, @Param("studiedSeconds") double studiedSeconds, @Param("playPosition") double playPosition, @Param("requestId") String requestId, @Param("markCompleted") boolean markCompleted, @Param("sessionId") Long sessionId, @Param("videoId") Long videoId);
}
