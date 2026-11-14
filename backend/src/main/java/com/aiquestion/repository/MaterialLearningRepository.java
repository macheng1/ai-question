package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for MaterialLearningService. SQL is defined in the matching mapper XML. */
@Mapper
public interface MaterialLearningRepository {
  List<Map<String, Object>> findSession(@Param("taskId") Long taskId, @Param("token") String token, @Param("examTask") boolean examTask);
  List<Map<String, Object>> findMaterials(@Param("taskId") Long taskId, @Param("examTask") boolean examTask, @Param("sessionId") Long sessionId);
  List<Map<String, Object>> findPageProgress(@Param("sessionId") Long sessionId, @Param("materialId") Long materialId);
  List<Integer> countAuthorizedPage(@Param("materialId") Long materialId, @Param("taskId") Long taskId, @Param("pageNumber") int pageNumber, @Param("examTask") boolean examTask, @Param("sessionId") Long sessionId);
  List<Integer> countIncompleteEarlierPages(@Param("materialId") Long materialId, @Param("pageNumber") int pageNumber, @Param("sessionId") Long sessionId);
  List<Map<String, Object>> lockMaterial(@Param("materialId") Long materialId);
  List<Map<String, Object>> lockSession(@Param("sessionId") Long sessionId);
  int initializePageProgress(@Param("sessionId") Long sessionId, @Param("materialId") Long materialId, @Param("pageNumber") int pageNumber);
  List<Map<String, Object>> lockPageProgress(@Param("sessionId") Long sessionId, @Param("materialId") Long materialId, @Param("pageNumber") int pageNumber);
  int clearHeartbeatTokens(@Param("sessionId") Long sessionId);
  int updatePageProgress(@Param("studiedSeconds") double studiedSeconds, @Param("completed") boolean completed, @Param("heartbeatToken") String heartbeatToken, @Param("consumedToken") String consumedToken, @Param("consumedSeconds") double consumedSeconds, @Param("markCompleted") boolean markCompleted, @Param("sessionId") Long sessionId, @Param("materialId") Long materialId, @Param("pageNumber") int pageNumber);
}
