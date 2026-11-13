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
    message.setSubject("【e-learning】培训逾期提醒：" + title.replaceAll("[\\r\\n]", " "));
    message.setText(
        name
            + "，您好：\n\n您参加的以下培训仍有逾期未完成事项，请查看详情，并联系培训管理员确认后续安排。\n\n培训名称："
            + title
            + "\n逾期事项："
            + String.join("、", reasons)
            + "\n培训结束时间："
            + trainingEnd
            + "\n考试结束时间："
            + examEnd
            + "\n\n查看培训详情与回看材料：\n"
            + publicUrl.replaceAll("/+$", "")
            + "/training/"
            + trainingId
            + "\n\n培训结束后可回看材料，不再记录学习进度或参加考试。如需继续学习或考试，请联系培训管理员。"
            + "\n\n如您已完成相关事项但状态未更新，请联系培训管理员核实。"
            + "\n\ne-learning 企业培训与考试平台");
    senders.getObject().send(message);
  }
}
