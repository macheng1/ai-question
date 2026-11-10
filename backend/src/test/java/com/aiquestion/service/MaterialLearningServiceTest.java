package com.aiquestion.service;
import com.aiquestion.common.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MaterialLearningServiceTest {
 @Test void limitsCreditToServerTime(){assertEquals(4,MaterialLearningService.creditedSeconds(4,10));assertEquals(3,MaterialLearningService.creditedSeconds(5,3));}
 @Test void doesNotCreditDisconnectedIntervals(){assertEquals(0,MaterialLearningService.creditedSeconds(60,10));}
 @Test void rejectsInvalidClientIntervals(){for(double n:new double[]{-1,16,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(BusinessException.class,()->MaterialLearningService.creditedSeconds(5,n));}
}
