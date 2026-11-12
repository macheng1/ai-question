package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TrainingMaterialService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TrainingMaterialRepository {
  List<Map<String, Object>> findMaterials(@Param("trainingId") Long trainingId);
  List<Map<String, Object>> findPages(@Param("materialId") Long materialId);
  List<Map<String, Object>> findMaterial(@Param("materialId") Long materialId, @Param("trainingId") Long trainingId);
  List<Map<String, Object>> lockTrainingForUpload(@Param("trainingId") Long trainingId);
  List<Integer> countTrainingSessions(@Param("trainingId") Long trainingId);
  int insertMaterial(@Param("trainingId") Long trainingId, @Param("title") String title, @Param("materialType") String materialType, @Param("storageKey") String storageKey, @Param("pageCount") int pageCount, @Param("generated") GeneratedId generated);
  int insertPage(@Param("materialId") Long materialId, @Param("pageNumber") int pageNumber);
  List<Map<String, Object>> lockTrainingForReorder(@Param("trainingId") Long trainingId);
  List<Long> findMaterialIds(@Param("trainingId") Long trainingId);
  int updateSortOrder(@Param("sortOrder") int sortOrder, @Param("materialId") Long materialId, @Param("trainingId") Long trainingId);
  List<Map<String, Object>> lockMaterialForTiming(@Param("materialId") Long materialId, @Param("trainingId") Long trainingId);
  List<Integer> countProgressForTiming(@Param("materialId") Long materialId);
  int updateRequiredSeconds(@Param("requiredSeconds") int requiredSeconds, @Param("materialId") Long materialId, @Param("pageNumber") int pageNumber);
  List<Map<String, Object>> lockMaterialForDelete(@Param("materialId") Long materialId, @Param("trainingId") Long trainingId);
  List<Integer> countProgressForDelete(@Param("materialId") Long materialId);
  int deleteMaterial(@Param("materialId") Long materialId);
}
