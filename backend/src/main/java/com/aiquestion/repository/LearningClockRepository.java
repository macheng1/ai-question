package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for LearningClock. SQL is defined in the matching mapper XML. */
@Mapper
public interface LearningClockRepository {
  List<String> findSessionPhone(@Param("sessionId") Long sessionId);
  int initializeClock(@Param("phone") String phone);
  List<Map<String, Object>> lockClock(@Param("phone") String phone);
  int touchClock(@Param("phone") String phone);
}
