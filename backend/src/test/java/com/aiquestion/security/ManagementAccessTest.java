package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.domain.TestPaper;
import com.aiquestion.repository.*;
import com.aiquestion.service.TestPaperService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagementAccessTest {
  @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }
  @Test void missingAndInvalidTokensAreRejected() {
    var tokens=mock(JwtTokenService.class);
    var jdbc=mock(JdbcTemplate.class);
    var access=new ManagementAccess(tokens,jdbc);
    var request=new MockHttpServletRequest();
    assertThrows(BusinessException.class,()->access.preHandle(request,new MockHttpServletResponse(),new Object()));
    request.addHeader("Authorization","Bearer broken");
    when(tokens.subject("broken",false)).thenThrow(new IllegalArgumentException());
    assertThrows(BusinessException.class,()->access.preHandle(request,new MockHttpServletResponse(),new Object()));
    verifyNoInteractions(jdbc);
  }
  @Test void inactiveUsersAreRejectedAndActiveIdentityIsExposed() {
    var tokens=mock(JwtTokenService.class);
    var jdbc=mock(JdbcTemplate.class);
    var access=new ManagementAccess(tokens,jdbc);
    var request=new MockHttpServletRequest();
    request.addHeader("Authorization","Bearer token");
    when(tokens.subject("token",false)).thenReturn("7");
    when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(7L))).thenReturn(0,1);
    assertThrows(BusinessException.class,()->access.preHandle(request,new MockHttpServletResponse(),new Object()));
    assertTrue(access.preHandle(request,new MockHttpServletResponse(),new Object()));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    assertEquals(7L,ManagementAccess.userId());
  }
  @Test void foreignAndUnownedPapersCannotBeReadOrDeleted() {
    var request=new MockHttpServletRequest();
    request.setAttribute("managementUserId",7L);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    var repo=mock(TestPaperRepository.class);
    var service=new TestPaperService(repo,mock(TestPaperQuestionRepository.class),mock(QuestionItemRepository.class),mock(QuestionOptionRepository.class),mock(JdbcTemplate.class));
    var paper=new TestPaper(); paper.setId(9L); paper.setCreatedBy(8L);
    when(repo.findById(9L)).thenReturn(paper);
    assertThrows(BusinessException.class,()->service.get(9L));
    assertThrows(BusinessException.class,()->service.delete(9L));
    paper.setCreatedBy(null);
    assertThrows(BusinessException.class,()->service.get(9L));
    verify(repo,never()).deleteById(anyLong());
    paper.setCreatedBy(7L);
    assertSame(paper,service.get(9L));
  }
}
