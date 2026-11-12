package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for ManagementOverviewService. SQL is defined in the matching mapper XML. */
@Mapper
public interface ManagementOverviewRepository {
  List<Long> countTrainings(@Param("ownerId") Long ownerId);
  List<Long> countEmployees(@Param("ownerId") Long ownerId);
  List<Long> countExams(@Param("ownerId") Long ownerId);
}
