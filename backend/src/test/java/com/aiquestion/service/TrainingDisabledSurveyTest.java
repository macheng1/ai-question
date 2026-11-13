package com.aiquestion.service;

import com.aiquestion.repository.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class TrainingDisabledSurveyTest {
    @Test void disablingClearsEditableQuestions() throws Exception { save(false, 0, false); }
    @Test void disabledIncompleteDraftDoesNotBlockSave() throws Exception { save(false, 0, true); }
    @Test void disablingPreservesResponsesQuestionDefinitions() throws Exception { save(false, 1, false); }
    @Test void startedTrainingCannotDisableSurvey() {
        assertThrows(com.aiquestion.common.BusinessException.class, () -> save(true, 0, false, true, false));
    }
    @Test void startedTrainingCannotEnableSurvey() {
        assertThrows(com.aiquestion.common.BusinessException.class, () -> save(true, 0, false, false, true));
    }
    @Test void startedTrainingCanSaveUnchangedSurvey() throws Exception { save(true, 0, false, true, true); }

    @Test void disabledExamDiscardsStaleConfigurationAndReloadsDefaults() {
        var tasks = mock(AssessmentTaskRepository.class);
        var papers = mock(TestPaperRepository.class);
        var service = new TrainingExamService(tasks, papers, mock(TrainingRepository.class),
                mock(AssessmentRosterService.class), mock(PublicExamService.class));
        var stale = new TrainingExamService.Settings(false, 99L, null, null, -1, true, 10, false, false, false, "旧考试");
        Long result = service.save(5L, stale, "培训", 2L, false);
        assertNull(result);
        var reloaded = service.get(result, 2L);
        assertFalse(reloaded.enabled());
        assertNull(reloaded.paperId());
        assertNull(reloaded.name());
        assertEquals(30, reloaded.durationMinutes());
        verifyNoInteractions(tasks, papers);
    }

    private void save(boolean started, long responses, boolean incomplete) throws Exception {
        save(started, responses, incomplete, false, false);
    }

    private void save(boolean started, long responses, boolean incomplete, boolean before, boolean after) throws Exception {
        var repository = mock(TrainingRepository.class);
        var audience = mock(TrainingAudienceService.class);
        var json = new ObjectMapper();
        var service = spy(new TrainingService(repository, json, mock(AssessmentRosterService.class), audience, mock(TrainingExamService.class)));
        doReturn(Map.of()).when(service).get(1L, 2L);
        var questions = List.of(new TrainingSurveyRules.Question("q1", "TEXT", "评价", false, List.of()));
        var stored = json.writeValueAsString(questions);
        var row = new HashMap<String, Object>(Map.of("created_by", 2L, "version", 1, "questions_json", stored, "survey_enabled", before));
        row.put("start_at", com.aiquestion.common.BusinessTime.now().plusDays(started ? -1 : 1));
        when(repository.findById(eq(1L), anyBoolean())).thenReturn(List.of(row));
        when(repository.countSurveyResponses(1L)).thenReturn(List.of(responses));
        var scope = new TrainingAudienceService.Scope("ALL", List.of());
        when(audience.get(1L, 2L)).thenReturn(scope);
        var submitted = incomplete ? List.of(new TrainingSurveyRules.Question("q2", "SINGLE", "", true, List.of())) : questions;
        service.save(1L, new TrainingService.SaveTraining("培训", "", after, submitted, 1, null,
                "UNCATEGORIZED", "ELECTIVE", scope, null, null), 2L);
        verify(repository).updateContent("培训", "", after, after || started || responses > 0 ? stored : "[]", 1L);
    }
}
