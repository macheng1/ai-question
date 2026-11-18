package com.aiquestion.integration;

import static org.junit.jupiter.api.Assertions.*;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

class MailFailureDetailsTest {
  @Test void explainsAuthenticationWithoutLeakingSecrets() {
    String reason = MailFailureDetails.describe(new MailAuthenticationException("password=private-value"));
    assertTrue(reason.contains("认证失败"));
    assertFalse(reason.contains("private-value"));
  }
  @Test void followsPerMessageFailures() {
    var exception = new MailSendException(Map.of("message", new SocketTimeoutException("Read timed out")));
    assertTrue(MailFailureDetails.describe(exception).contains("超时"));
  }
  @Test void exposesSmtpCodeWithoutEchoingServerText() {
    var exception = new MailSendException("failed", new jakarta.mail.MessagingException("550 recipient private@example.com rejected"));
    String reason = MailFailureDetails.describe(exception);
    assertTrue(reason.contains("550"));
    assertFalse(reason.contains("private@example.com"));
  }
}
