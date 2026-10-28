package com.aiquestion.service;
import com.aiquestion.common.BusinessException;
import com.aiquestion.domain.AssessmentTask;
import com.aiquestion.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TrainingAssessmentDeleteTest {
  @Test void linkedAssessmentCannotBeDeleted() {
    var request=new MockHttpServletRequest();request.setAttribute("managementUserId",7L);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      var repo=mock(AssessmentTaskRepository.class);var task=new AssessmentTask();task.setId(1L);task.setCreatedBy(7L);
      when(repo.findById(1L)).thenReturn(task);when(repo.countTrainingReferences(1L)).thenReturn(1);
      var service=new AssessmentTaskService(repo,mock(TestPaperRepository.class));
      assertThrows(BusinessException.class,()->service.delete(1L));verify(repo,never()).deleteById(anyLong());
    } finally {RequestContextHolder.resetRequestAttributes();}
  }
}
