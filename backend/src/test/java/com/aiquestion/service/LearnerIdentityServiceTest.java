package com.aiquestion.service;
import com.aiquestion.common.BusinessException;
import com.aiquestion.security.AccountType;
import org.junit.jupiter.api.Test;
import com.aiquestion.repository.LearnerIdentityRepository;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LearnerIdentityServiceTest {
  private final LearnerIdentityRepository jdbc = mock(LearnerIdentityRepository.class);
  private final AccountService accounts = mock(AccountService.class);
  private final LearnerIdentityService service = new LearnerIdentityService(jdbc, accounts, mock(AssessmentRosterService.class), mock(TrainingAudienceService.class));
  private final AccountService.Profile employee = new AccountService.Profile(7, "张明", "13900000001", null, "企业", "部门", AccountType.EMPLOYEE, 3);
  @Test void candidateMustMatchSession() {
    assertDoesNotThrow(() -> service.requireCandidate(employee, " 张明 ", "13900000001"));
    assertThrows(BusinessException.class, () -> service.requireCandidate(employee, "李明", "13900000001"));
  }
  @Test void otherEmployeesAttemptIsRejected() {
    when(jdbc.countOwnedAttempt(21L, 7L, 3L)).thenReturn(List.of(0));
    assertThrows(BusinessException.class, () -> service.requireAttempt(employee, 21));
  }
  @Test void ownAttemptIsAllowed() {
    when(jdbc.countOwnedAttempt(21L, 7L, 3L)).thenReturn(List.of(1));
    assertDoesNotThrow(() -> service.requireAttempt(employee, 21));
  }
  @Test void unknownTrainingCannotIssueIdentity() {
    when(jdbc.findEntryOwner(99L, true)).thenReturn(List.of());
    assertThrows(BusinessException.class, () -> service.confirm("TRAINING", 99, "张明", "13900000001"));
    verifyNoInteractions(accounts);
  }
  @Test void unknownEntryTypeIsRejected() {
    assertThrows(BusinessException.class, () -> service.confirm("ADMIN", 3, "张明", "13900000001"));
    verifyNoInteractions(jdbc, accounts);
  }
}
