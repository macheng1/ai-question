package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for PublicLearningService. SQL is defined in the matching mapper XML. */
@Mapper
public interface PublicLearningRepository {
  List<Map<String, Object>> findTrainingEntry(@Param("trainingId") Long trainingId);
  List<Long> findMaterialTraining(@Param("materialId") Long materialId);
}
