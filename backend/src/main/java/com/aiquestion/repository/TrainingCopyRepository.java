package com.aiquestion.repository;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for TrainingCopyService. SQL is defined in the matching mapper XML. */
@Mapper
public interface TrainingCopyRepository {
  List<Map<String, Object>> lockOwnedAssessment(@Param("assessmentId") Long assessmentId, @Param("ownerId") Long ownerId);
  List<Map<String, Object>> lockOwnedTraining(@Param("trainingId") Long trainingId, @Param("ownerId") Long ownerId);
  int copyTraining(
      @Param("name") String name,
      @Param("sourceTrainingId") Long sourceTrainingId,
      @Param("generated") GeneratedId generated);
  int resetAudience(@Param("trainingId") Long trainingId);
  List<Map<String, Object>> findSourceVideos(@Param("trainingId") Long trainingId);
  int insertVideo(
      @Param("trainingId") Long trainingId,
      @Param("title") String title,
      @Param("storagePath") String storagePath,
      @Param("contentType") String contentType,
      @Param("sortOrder") Integer sortOrder);
  List<Map<String, Object>> findSourceMaterials(@Param("trainingId") Long trainingId);
  int insertMaterial(
      @Param("trainingId") Long trainingId,
      @Param("title") String title,
      @Param("materialType") String materialType,
      @Param("storageKey") String storageKey,
      @Param("pageCount") Integer pageCount,
      @Param("sortOrder") Integer sortOrder,
      @Param("generated") GeneratedId generated);
  int copyMaterialPages(@Param("materialId") Long materialId, @Param("sourceMaterialId") Long sourceMaterialId);
}
