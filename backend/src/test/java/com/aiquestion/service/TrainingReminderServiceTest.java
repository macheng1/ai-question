package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiquestion.common.*;
import com.aiquestion.integration.TrainingReminderMailer;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.aiquestion.repository.TrainingReminderRepository;

class TrainingReminderServiceTest {
  @Test
  void permissionCheckedBeforeReadingRecipients() {
    var jdbc = mock(TrainingReminderRepository.class);
    var trainings = mock(TrainingService.class);
    when(trainings.get(1L, 2L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));
    var service =
        new TrainingReminderService(
            jdbc, trainings, mock(TrainingLogService.class), mock(TrainingReminderMailer.class));
    assertThrows(BusinessException.class, () -> service.preview(1, 2));
    verifyNoInteractions(jdbc);
  }

  @Test
  void invalidSelectionCannotSend() {
    var mailer = mock(TrainingReminderMailer.class);
    var service =
        new TrainingReminderService(
            mock(TrainingReminderRepository.class),
            mock(TrainingService.class),
            mock(TrainingLogService.class),
            mailer);
    assertThrows(
        BusinessException.class,
        () -> service.send(1, 2, new TrainingReminderService.SendRequest("invalid", List.of(1L))));
    verifyNoInteractions(mailer);
  }

  @Test
  void rejectsMissingMalformedAndHeaderInjectedEmails() {
    assertFalse(TrainingReminderMailer.validEmail(null));
    assertFalse(TrainingReminderMailer.validEmail("not-an-email"));
    assertFalse(TrainingReminderMailer.validEmail("one@example.com\r\nBcc: other@example.com"));
    assertTrue(TrainingReminderMailer.validEmail("learner@example.com"));
  }
}
