package com.aiquestion.integration;

import com.aiquestion.common.*;
import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 仅负责邮件通道，资格筛选及发送记录由业务服务负责。 */
@Component
public class TrainingReminderMailer {
  private final ObjectProvider<JavaMailSender> senders;
  private final String host, from, publicUrl;

  public TrainingReminderMailer(
      ObjectProvider<JavaMailSender> senders,
      @Value("${spring.mail.host:}") String host,
      @Value("${app.reminder.from:}") String from,
      @Value("${app.reminder.public-url:}") String publicUrl) {
    this.senders = senders;
    this.host = host;
    this.from = from;
    this.publicUrl = publicUrl;
  }

  public static boolean validEmail(String email) {
    if (email == null
        || email.isBlank()
        || email.length() > 254
        || email.contains("\n")
        || email.contains("\r")) return false;
    try {
      var address = new InternetAddress(email, true);
      address.validate();
      return address.getAddress().equals(email) && email.contains("@");
    } catch (jakarta.mail.internet.AddressException e) {
      return false;
    }
  }

  public boolean configured() {
    try {
      var uri = URI.create(publicUrl);
      return !host.isBlank()
          && validEmail(from)
          && senders.getIfAvailable() != null
          && List.of("http", "https").contains(uri.getScheme())
          && uri.getHost() != null
          && uri.getQuery() == null
          && uri.getFragment() == null;
    } catch (IllegalArgumentException | NullPointerException e) {
      return false;
    }
  }

  public void requireConfigured() {
    if (!configured())
      throw new BusinessException(ErrorCode.BAD_REQUEST, "邮件服务未配置完整，请配置发件邮箱和平台访问地址");
  }

  public void send(
      String email,
      String name,
      long trainingId,
      String title,
      List<String> reasons,
      String trainingEnd,
      String examEnd) {
    requireConfigured();
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject("培训待办提醒：" + title.replaceAll("[\\r\\n]", " "));
    message.setText(
        name
            + "，你好：\n\n培训："
            + title
            + "\n待完成事项："
            + String.join("、", reasons)
            + "\n培训结束时间："
            + trainingEnd
            + "\n考试结束时间："
            + examEnd
            + "\n\n培训逾期后仍可补学。若考试已经结束，请联系管理员安排后续考试。"
            + "\n请登录员工账号查看："
            + publicUrl.replaceAll("/+$", "")
            + "/training/"
            + trainingId);
    senders.getObject().send(message);
  }
}
