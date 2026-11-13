package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.repository.*;
import com.aiquestion.storage.FileStorageService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingCopyServiceTest {
  private final TrainingCopyRepository repository = mock(TrainingCopyRepository.class);
  private final TrainingService trainings = mock(TrainingService.class);
  private final TrainingFileCopyService files = mock(TrainingFileCopyService.class);
  private final FileStorageService storage = mock(FileStorageService.class);
  private final TrainingCopyService service = new TrainingCopyService(repository, trainings, files, storage, mock(TrainingEligibilityService.class), mock(TrainingEndpointService.class), mock(TrainingMaterialService.class));

  @BeforeEach void setup() {
    TransactionSynchronizationManager.initSynchronization();
    when(trainings.get(1L, 2L)).thenReturn(Map.of("id", 1L));
    when(repository.lockOwnedTraining(1L, 2L)).thenReturn(List.of(Map.of("name", "消防培训", "created_by", 2L)));
    doAnswer(call -> { call.getArgument(2, GeneratedId.class).setId(100L); return 1; })
        .when(repository).copyTraining(anyString(), anyLong(), any());
    when(trainings.get(100L, 2L)).thenReturn(Map.of("id", 100L, "name", "消防培训（副本）"));
  }
  @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

  @Test void standaloneCopyCreatesFreshAudienceAndDoesNotCopyRecords() {
    assertEquals(100L, service.copy(1L, 2L).get("id"));
    verify(repository).resetAudience(100L);
    verify(repository).copyTraining(eq("消防培训（副本）"), eq(1L), any());
    verifyNoInteractions(files);
  }

  @Test void linkedTrainingCopyLeavesExamEmptyAndDoesNotCreateAnExam() {
    when(trainings.get(1L, 2L)).thenReturn(Map.of("id", 1L, "assessmentId", 9L));
    when(repository.lockOwnedTraining(1L, 2L)).thenReturn(List.of(Map.of("name", "消防培训", "created_by", 2L, "assessment_id", 9L)));
    when(repository.lockOwnedAssessment(9L, 2L)).thenReturn(List.of(Map.of("id", 9L, "name", "消防考核")));
    service.copy(1L, 2L);
    verify(repository).copyTraining(eq("消防培训（副本）"), eq(1L), any());
    verify(repository).resetAudience(100L);
  }

  @Test void unauthorizedCopyDoesNotWriteOrCopyObjects() {
    when(trainings.get(1L, 2L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
    assertThrows(BusinessException.class, () -> service.copy(1L, 2L));
    verifyNoInteractions(repository, files);
  }

  @Test void rollbackDeletesOnlyNewObjects() {
    var video = Map.<String,Object>of("id", 7L, "title", "视频", "storage_path", "oss:old", "content_type", "video/mp4", "sort_order", 0);
    when(repository.findSourceVideos(1L)).thenReturn(List.of(video));
    when(files.copyVideo(video)).thenReturn("oss:new");
    service.copy(1L, 2L);
    TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    verify(storage).delete("oss:new");
    verify(storage, never()).delete("oss:old");
  }

  @Test void materialCopiesEveryPageTimingWithNewMaterialId() {
    var material = Map.<String,Object>of("id", 7L, "title", "培训.pdf", "storage_key", "oss:old.pdf", "material_type", "PDF", "page_count", 3, "sort_order", 2);
    when(repository.findSourceMaterials(1L)).thenReturn(List.of(material));
    when(files.copyMaterial(material)).thenReturn("oss:new.pdf");
    doAnswer(call -> { call.getArgument(6, GeneratedId.class).setId(101L); return 1; })
        .when(repository).insertMaterial(anyLong(), anyString(), anyString(), anyString(), anyInt(), anyInt(), any());
    service.copy(1L, 2L);
    verify(repository).insertMaterial(eq(100L), eq("培训.pdf"), eq("PDF"), eq("oss:new.pdf"), eq(3), eq(2), any());
    verify(repository).copyMaterialPages(101L, 7L);
  }

  @Test void copyDoesNotInheritOldAudienceEvenWhenItIsInvalid() {
    service.copy(1L, 2L);
    verify(repository).resetAudience(100L);
    verify(repository).copyTraining(eq("消防培训（副本）"), eq(1L), any());
  }

  @Test void rejectsConcurrentExamChangeBeforeWriting() {
    when(repository.lockOwnedTraining(1L, 2L)).thenReturn(List.of(Map.of("name", "培训", "assessment_id", 9L)));
    assertThrows(BusinessException.class, () -> service.copy(1L, 2L));
    verify(repository, never()).copyTraining(any(), any(), any());
    verifyNoInteractions(files);
  }

  @Test void commitRetainsCopiedObjects() {
    service.copy(1L, 2L);
    TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    verifyNoInteractions(storage);
  }
}
