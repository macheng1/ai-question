package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for LearningClock. SQL is defined in the matching mapper XML. */
@Mapper
public interface LearningClockRepository {
  List<Long> findSessionEmployeeId(@Param("sessionId") Long sessionId);
  int initializeClock(@Param("employeeId") long employeeId);
  List<Map<String, Object>> lockClock(@Param("employeeId") long employeeId);
  int touchClock(@Param("employeeId") long employeeId);
}
