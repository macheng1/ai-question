package com.aiquestion.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;

class TrainingReminderMailerTest {
  @SuppressWarnings("unchecked")
  private TrainingReminderMailer mailer(JavaMailSender sender) {
    var provider = (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(sender);
    when(provider.getObject()).thenReturn(sender);
    return new TrainingReminderMailer(provider, "smtp.example.com", "sender@example.com", "https://example.com");
  }

  @Test void rosterRetainsOnlyFailedRecipientsAndContinuesSending() {
    var sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
    doThrow(new MailAuthenticationException("private credential")).doNothing().when(sender).send(any(MimeMessage.class));
    var result = mailer(sender).sendOverdueRoster(List.of("a@example.com", "b@example.com"), new byte[]{1}, 2);
    assertEquals(1, result.size());
    assertEquals("a@example.com", result.get(0).email());
    assertTrue(result.get(0).reason().contains("认证失败"));
    verify(sender, times(2)).send(any(MimeMessage.class));
  }

  @Test void learnerReportsErrorAndSuccessSeparately() {
    var sender = mock(JavaMailSender.class);
    doThrow(new MailSendException("failed", new java.net.SocketTimeoutException())).doNothing()
        .when(sender).send(any(SimpleMailMessage.class));
    var mailer = mailer(sender);
    List<Map<String,Object>> rows = List.of(Map.of("name", "学员", "trainingName", "培训", "trainingId", 1));
    assertTrue(mailer.sendLearnerOverdue("a@example.com", rows).contains("超时"));
    assertNull(mailer.sendLearnerOverdue("a@example.com", rows));
  }
}
