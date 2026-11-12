package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for PublicSelfTestService. SQL is defined in the matching mapper XML. */
@Mapper
public interface PublicSelfTestRepository {
  List<Long> countPublishedTests(@Param("search") String search, @Param("nameSearch") String nameSearch, @Param("descriptionSearch") String descriptionSearch, @Param("bankSearch") String bankSearch);
  List<Map<String, Object>> findPublishedTests(@Param("search") String search, @Param("nameSearch") String nameSearch, @Param("descriptionSearch") String descriptionSearch, @Param("bankSearch") String bankSearch, @Param("limit") int limit, @Param("offset") long offset);
  List<Map<String, Object>> findPublishedTest(@Param("testId") Long testId);
}
