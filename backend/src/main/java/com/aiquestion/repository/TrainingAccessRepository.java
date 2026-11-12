package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TrainingEndpointService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TrainingAccessRepository {
  List<Integer> countActiveAdministrator(@Param("userId") Long userId);
}
