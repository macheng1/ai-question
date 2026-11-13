package com.aiquestion.service;
import org.junit.jupiter.api.Test;
import com.aiquestion.common.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
class CandidateLearningServiceTest {
 @Test void rejectsJumpAndInvalidCompletion() {
  assertThrows(BusinessException.class,()->CandidateLearningService.validateProgress(100,100,0,1,true));
  assertThrows(BusinessException.class,()->CandidateLearningService.validateProgress(10,100,0,10,true));
  assertThrows(BusinessException.class,()->CandidateLearningService.validateProgress(Double.NaN,100,0,10,true));
  assertThrows(BusinessException.class,()->CandidateLearningService.validateProgress(10,Double.POSITIVE_INFINITY,0,10,true));
  assertThrows(BusinessException.class,()->CandidateLearningService.validateProgress(0,0,0,10,true));
  assertDoesNotThrow(()->CandidateLearningService.validateProgress(100,100,90,10,true));
 }
 @Test void noLinkedVideosAllowStarting() {
  var repository = org.mockito.Mockito.mock(com.aiquestion.repository.CandidateLearningRepository.class);
  var audience = org.mockito.Mockito.mock(TrainingAudienceService.class);
  org.mockito.Mockito.when(repository.countIncompleteVideos(1L,1L,"A","1",true)).thenReturn(java.util.List.of(0));
  org.mockito.Mockito.when(repository.countIncompletePages(1L,1L,"A","1",true)).thenReturn(java.util.List.of(0));
  var service = new CandidateLearningService(repository, org.mockito.Mockito.mock(AssessmentRosterService.class), org.mockito.Mockito.mock(LearningClock.class), org.mockito.Mockito.mock(MaterialLearningService.class), audience);
  assertDoesNotThrow(()->service.requireComplete(1,"A","1"));
  org.mockito.Mockito.verify(audience).requireOpenTask(1L,"A","1");
  org.mockito.Mockito.verify(repository).confirmSessionsCompleted(1L,"A","1",true);
 }
 @Test void repeatedRequestsDoNotReceiveRepeatedTolerance() {
  // Both requests retain the same persisted baseline, even after the first save.
  assertDoesNotThrow(()->CandidateLearningService.validateProgress(2,100,0,0,false));
  assertThrows(BusinessException.class,()->CandidateLearningService.validateProgress(4,100,0,0.01,false));
  assertDoesNotThrow(()->CandidateLearningService.validateProgress(4,100,0,2,false));
 }
 @Test void rejectsInvalidPhone() {
  assertThrows(BusinessException.class,()->CandidateLearningService.validate("Alice","123"));
  assertThrows(BusinessException.class,()->CandidateLearningService.validate("Alice","23800000000"));
  assertDoesNotThrow(()->CandidateLearningService.validate("Alice","13800000000"));
 }
 @Test void rejectsMissingIdentity() {
  assertThrows(BusinessException.class,()->CandidateLearningService.validate(null,"123"));
  assertThrows(BusinessException.class,()->CandidateLearningService.validate("Alice"," "));
 }
}
