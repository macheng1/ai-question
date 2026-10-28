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
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
  org.mockito.Mockito.when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(Integer.class),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.eq("A"),org.mockito.ArgumentMatchers.eq("1"))).thenReturn(0);
  assertDoesNotThrow(()->new CandidateLearningService(jdbc).requireComplete(1,"A","1"));
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
