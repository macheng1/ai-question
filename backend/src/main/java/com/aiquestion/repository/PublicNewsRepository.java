package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for PublicNewsService. SQL is defined in the matching mapper XML. */
@Mapper
public interface PublicNewsRepository {
  List<Map<String, Object>> findPublishedNews();
  List<Map<String, Object>> findPublishedNewsById(@Param("newsId") Long newsId);
}
