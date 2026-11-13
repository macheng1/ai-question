package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for PublicExamService. SQL is defined in the matching mapper XML. */
@Mapper
public interface PublicExamRepository {
  List<Long> findOpenTaskAttempts(@Param("taskId") Long taskId);
  List<Map<String, Object>> findAttemptStatus(@Param("attemptId") Long attemptId, @Param("taskId") Long taskId);
  List<Map<String, Object>> findAttemptCandidate(@Param("attemptId") Long attemptId, @Param("taskId") Long taskId);
  List<Map<String, Object>> findQuestionOptions(@Param("questionId") Long questionId);
  List<Map<String, Object>> findLatestParticipant(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone);
  int insertParticipant(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone, @Param("generated") GeneratedId generated);
  int assignParticipantEmployee(@Param("participantId") Long participantId);
  List<Map<String, Object>> findActiveAttempt(@Param("taskId") Long taskId, @Param("participantId") Long participantId);
  List<Map<String, Object>> findLatestSubmittedAttempt(@Param("taskId") Long taskId, @Param("participantId") Long participantId);
  List<Map<String, Object>> findLatestAttemptAnswers(@Param("attemptId") Long attemptId);
  int insertAttemptFromTask(@Param("taskId") Long taskId, @Param("participantId") Long participantId, @Param("attemptNumber") int attemptNumber, @Param("sourceTaskId") Long sourceTaskId, @Param("generated") GeneratedId generated);
  List<Object> findAttemptStartedAt(@Param("attemptId") Long attemptId);
  int updateParticipantAttemptCount(@Param("attemptCount") int attemptCount, @Param("participantId") Long participantId);
  List<Map<String, Object>> findStartedAttemptAnswers(@Param("attemptId") Long attemptId);
  List<Map<String, Object>> findEligibilityParticipant(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone);
  List<Map<String, Object>> findAnyActiveAttempt(@Param("taskId") Long taskId, @Param("participantId") Long participantId);
  List<Map<String, Object>> findCandidateActiveAttempt(@Param("taskId") Long taskId, @Param("candidateName") String candidateName, @Param("phone") String phone);
  List<Map<String, Object>> findProgressAnswers(@Param("attemptId") Long attemptId);
  List<Map<String, Object>> findQuestionForAnswer(@Param("attemptId") Long attemptId, @Param("questionId") Long questionId);
  int upsertAnswer(@Param("attemptId") Long attemptId, @Param("questionId") Long questionId, @Param("answer") String answer, @Param("correctAnswer") String correctAnswer, @Param("correct") boolean correct, @Param("score") java.math.BigDecimal score);
  List<Map<String, Object>> findAttemptForSubmission(@Param("attemptId") Long attemptId);
  List<Integer> countAttemptQuestions(@Param("attemptId") Long attemptId);
  List<Integer> countAnsweredQuestions(@Param("attemptId") Long attemptId);
  List<BigDecimal> sumAnswerScores(@Param("attemptId") Long attemptId);
  int submitAttempt(@Param("score") java.math.BigDecimal score, @Param("passed") boolean passed, @Param("attemptId") Long attemptId);
  List<Map<String, Object>> findCandidateRecords(@Param("taskId") Long taskId, @Param("phone") String phone);
  List<Map<String, Object>> findAttemptResult(@Param("attemptId") Long attemptId);
  List<Map<String, Object>> findResultAnswers(@Param("attemptId") Long attemptId);
  List<Map<String, Object>> findTaskDetails(@Param("taskId") Long taskId);
  List<Map<String, Object>> findQuestions(@Param("sourceId") Long sourceId, @Param("paperQuestions") boolean paperQuestions);
  List<Integer> countSnapshotQuestions(@Param("attemptId") Long attemptId);
  List<Long> findTaskPaper(@Param("taskId") Long taskId);
  int snapshotFixedQuestions(@Param("attemptId") Long attemptId, @Param("paperId") Long paperId);
  List<Map<String, Object>> findRandomBankConfig(@Param("paperId") Long paperId);
  List<Map<String, Object>> findEnabledQuestionIds(@Param("bankId") Long bankId, @Param("questionType") String questionType);
  int insertSnapshotQuestion(@Param("attemptId") Long attemptId, @Param("questionId") Long questionId, @Param("score") java.math.BigDecimal score, @Param("sortOrder") int sortOrder);
  List<Map<String, Object>> findAttemptIdentity(@Param("attemptId") Long attemptId);
  List<Map<String, Object>> lockAttempt(@Param("attemptId") Long attemptId);
}
