package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for ProfileRecordService. SQL is defined in the matching mapper XML. */
@Mapper
public interface ProfileRecordRepository {
  List<Map<String, Object>> findRecords(@Param("userId") Long userId, @Param("phone") String phone);
  List<Map<String, Object>> findLatestMistakes(@Param("latestUserId") Long latestUserId, @Param("latestPhone") String latestPhone, @Param("userId") Long userId, @Param("phone") String phone);
  List<Map<String, Object>> findQuestionOptions(@Param("questionId") Long questionId);
  List<Map<String, Object>> findUserPhone(@Param("userId") Long userId);
}
