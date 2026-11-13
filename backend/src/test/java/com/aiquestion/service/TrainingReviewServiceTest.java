package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.*;
import com.aiquestion.security.AccountIdentity;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainingReviewServiceTest {
    final LearnerRecordService records = mock(LearnerRecordService.class);
    final TrainingRepository trainings = mock(TrainingRepository.class);
    final TrainingMaterialRepository materials = mock(TrainingMaterialRepository.class);
    final TrainingAudienceRepository audience = mock(TrainingAudienceRepository.class);
    final AccountIdentity identity = mock(AccountIdentity.class);
    final TrainingReviewService service = new TrainingReviewService(records, trainings, materials, audience);

    @Test void endedAssignedTrainingCanBeReviewedWithoutWritingProgress() {
        when(records.trainings(identity)).thenReturn(List.of(Map.of("id", 69L, "trainingEnded", true)));
        when(audience.countNotStartedTrainings(69L, false)).thenReturn(List.of(0));
        when(trainings.findReviewVideos(69L)).thenReturn(List.of(Map.of("id", 3L, "title", "视频")));
        when(materials.findMaterials(69L)).thenReturn(List.of(Map.of("id", 4L, "pageCount", 12)));
        var result = service.list(identity, 69L);
        assertEquals(List.of(Map.of("id", 3L, "title", "视频")), result.get("videos"));
        assertEquals(1, ((List<?>) result.get("materials")).size());
        verify(trainings).findReviewVideos(69L);
        verify(materials).findMaterials(69L);
        verifyNoMoreInteractions(trainings, materials);
    }
    @Test void unassignedTrainingCannotReadAnyMaterials() {
        when(records.trainings(identity)).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.list(identity, 69L));
        assertThrows(BusinessException.class, () -> service.video(identity, 69L, 3L));
        verifyNoInteractions(trainings, materials, audience);
    }
    @Test void futureTrainingIsNotOpenedByReview() {
        when(records.trainings(identity)).thenReturn(List.of(Map.of("id", 69L)));
        when(audience.countNotStartedTrainings(69L, false)).thenReturn(List.of(1));
        assertThrows(BusinessException.class, () -> service.list(identity, 69L));
        verifyNoInteractions(trainings, materials);
    }
    @Test void videoMustBelongToRequestedTraining() {
        when(records.trainings(identity)).thenReturn(List.of(Map.of("id", 69L)));
        when(audience.countNotStartedTrainings(69L, false)).thenReturn(List.of(0));
        when(trainings.findVideo(69L, 999L)).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.video(identity, 69L, 999L));
    }
}
