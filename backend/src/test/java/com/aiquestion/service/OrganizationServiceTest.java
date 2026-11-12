package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrganizationServiceTest {
  @org.junit.jupiter.api.AfterEach
  void clearRequest() { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

  private OrganizationRepository employeeDatabase() {
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setAttribute("managementUserId", 1L);
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
        new org.springframework.web.context.request.ServletRequestAttributes(request));
    var repository = mock(OrganizationRepository.class);
    when(repository.countOwnedDepartments(anyLong(), anyLong(), anyBoolean())).thenReturn(List.of(1));
    when(repository.lockDepartmentStatus(anyLong(), eq(1L))).thenReturn(List.of("ACTIVE"));
    when(repository.countPhoneConflicts(anyString(), anyLong())).thenReturn(List.of(0));
    when(repository.findEmployees(1L)).thenReturn(List.of(Map.of("id", 2L)));
    doAnswer(call -> { call.getArgument(11, GeneratedId.class).setId(2L); return 1; })
        .when(repository).insertEmployee(anyLong(), anyLong(), anyString(), anyString(), any(), any(), anyString(), anyString(), anyString(), any(), any(), any());
    return repository;
  }

  @Test void newEmployeeStoresTrimmedEmailWithoutPassword() {
    var repository = employeeDatabase();
    var assignments = mock(RequiredTrainingAssignmentService.class);
    var saved = new OrganizationService(repository, assignments).saveEmployee(null, "Alice", "13912345678", " alice@example.com ", null, 1L, "ACTIVE", "EXTERNAL", "FEMALE", " A001 ", " 备注 ");
    assertEquals(2L, saved.get("id"));
    verify(repository).insertEmployee(eq(1L), eq(1L), eq("Alice"), eq("13912345678"), eq("alice@example.com"), isNull(), eq("ACTIVE"), eq("EXTERNAL"), eq("FEMALE"), eq("A001"), eq("备注"), any());
    verify(assignments).assignEmployee(2L, 1L);
  }

  @Test void editingEmployeeUpdatesOnlyProfileFields() {
    var repository = employeeDatabase();
    when(repository.findEmployeeDepartment(2L, 1L)).thenReturn(List.of(Map.of("department_id", 1L, "status", "ACTIVE")));
    when(repository.countOwnedEmployees(2L, 1L)).thenReturn(List.of(1));
    new OrganizationService(repository, mock(RequiredTrainingAssignmentService.class)).saveEmployee(2L, "Alice", "13912345678", "new@example.com", null, 1L, "ACTIVE", "EXTERNAL", "FEMALE", " A001 ", " 备注 ");
    verify(repository).updateEmployee(1L, "Alice", "13912345678", "new@example.com", null, "ACTIVE", "EXTERNAL", "FEMALE", "A001", "备注", 2L, 1L);
  }

  @Test void invalidEmailRejectedBeforeDatabaseAccess() {
    var repository = mock(OrganizationRepository.class);
    var service = new OrganizationService(repository, mock(RequiredTrainingAssignmentService.class));
    assertThrows(BusinessException.class, () -> service.saveEmployee(null, "Alice", "13912345678", "bad-email", null, 1L, "ACTIVE", null, null, null, null));
    verifyNoInteractions(repository);
  }

  @Test void invalidProfileFieldsRejectedBeforeDatabaseAccess() {
    var repository = mock(OrganizationRepository.class);
    var service = new OrganizationService(repository, mock(RequiredTrainingAssignmentService.class));
    assertThrows(BusinessException.class, () -> service.saveEmployee(null, "Alice", "13912345678", null, null, 1L, "ACTIVE", "OTHER", null, null, null));
    assertThrows(BusinessException.class, () -> service.saveEmployee(null, "Alice", "13912345678", null, null, 1L, "ACTIVE", null, "OTHER", null, null));
    assertThrows(BusinessException.class, () -> service.saveEmployee(null, "Alice", "13912345678", null, null, 1L, "ACTIVE", null, null, "a".repeat(101), null));
    assertThrows(BusinessException.class, () -> service.saveEmployee(null, "Alice", "13912345678", null, null, 1L, "ACTIVE", null, null, null, "a".repeat(1001)));
    verifyNoInteractions(repository);
  }

  @Test void statusContractAcceptsActiveAndInactiveOnly() {
    assertDoesNotThrow(() -> OrganizationService.validateStatus("ACTIVE"));
    assertDoesNotThrow(() -> OrganizationService.validateStatus("INACTIVE"));
    for (String status : new String[] {null, "DISABLED", "active", ""})
      assertThrows(BusinessException.class, () -> OrganizationService.validateStatus(status));
  }

  @Test void invalidPhoneRejectedBeforeDatabaseAccess() {
    var repository = mock(OrganizationRepository.class);
    assertThrows(BusinessException.class, () -> new OrganizationService(repository, mock(RequiredTrainingAssignmentService.class))
        .saveEmployee(null, "Alice", "123", null, null, 1L, "ACTIVE", null, null, null, null));
    verifyNoInteractions(repository);
  }

  @Test void blankDepartmentNameRejectedBeforeDatabaseAccess() {
    var repository = mock(OrganizationRepository.class);
    assertThrows(BusinessException.class, () -> new OrganizationService(repository, mock(RequiredTrainingAssignmentService.class))
        .saveDepartment(null, "  ", "ACTIVE"));
    verifyNoInteractions(repository);
  }
  @Test void renamingDepartmentDoesNotAssignAnEmployeeWithTheSameId() {
    var repository = employeeDatabase();
    when(repository.findDepartmentsWithEmployeeCounts(1L))
        .thenReturn(List.of(Map.of("id", 5L, "status", "ACTIVE")));
    var assignments = mock(RequiredTrainingAssignmentService.class);
    new OrganizationService(repository, assignments).saveDepartment(5L, "研发部", "ACTIVE");
    verify(assignments).lockAllocationOwner(1L);
    verifyNoMoreInteractions(assignments);
  }

  @Test void restoringDepartmentAssignsItsEmployeesAfterUpdatingStatus() {
    var repository = employeeDatabase();
    when(repository.lockDepartmentStatus(5L, 1L)).thenReturn(List.of("INACTIVE"));
    when(repository.findDepartmentsWithEmployeeCounts(1L))
        .thenReturn(List.of(Map.of("id", 5L, "status", "ACTIVE")));
    var assignments = mock(RequiredTrainingAssignmentService.class);
    new OrganizationService(repository, assignments).saveDepartment(5L, "研发部", "ACTIVE");
    var order = inOrder(repository, assignments);
    order.verify(assignments).lockAllocationOwner(1L);
    order.verify(repository).updateDepartment("研发部", "ACTIVE", 5L, 1L);
    order.verify(assignments).assignDepartment(5L, 1L);
    verifyNoMoreInteractions(assignments);
  }

  @Test void disablingOrCreatingDepartmentDoesNotAssignEmployees() {
    var repository = employeeDatabase();
    when(repository.findDepartmentsWithEmployeeCounts(1L))
        .thenReturn(List.of(Map.of("id", 5L, "status", "INACTIVE")));
    doAnswer(call -> { call.getArgument(3, GeneratedId.class).setId(5L); return 1; })
        .when(repository).insertDepartment(eq(1L), anyString(), anyString(), any());
    var assignments = mock(RequiredTrainingAssignmentService.class);
    var service = new OrganizationService(repository, assignments);
    service.saveDepartment(5L, "研发部", "INACTIVE");
    service.saveDepartment(null, "研发部", "ACTIVE");
    verify(assignments, times(2)).lockAllocationOwner(1L);
    verifyNoMoreInteractions(assignments);
  }

  @Test void unownedDepartmentCannotBeUpdatedOrAssigned() {
    var repository = employeeDatabase();
    when(repository.lockDepartmentStatus(5L, 1L)).thenReturn(List.of());
    var assignments = mock(RequiredTrainingAssignmentService.class);
    assertThrows(BusinessException.class,
        () -> new OrganizationService(repository, assignments).saveDepartment(5L, "研发部", "ACTIVE"));
    verify(repository, never()).updateDepartment(any(), any(), any(), any());
    verify(assignments).lockAllocationOwner(1L);
    verifyNoMoreInteractions(assignments);
  }

}
