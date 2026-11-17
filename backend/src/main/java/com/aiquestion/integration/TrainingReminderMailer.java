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

  /** 每个收件人单独发送，返回发送失败的地址，供管理端定向重试。 */
  public List<String> sendOverdueRoster(List<String> recipients, byte[] workbook, int rows) {
    if (recipients == null || recipients.isEmpty())
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少选择一个收件邮箱");
    var addresses = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    for (String address : recipients) {
      if (address == null || !validEmail(address.trim()))
        throw new BusinessException(ErrorCode.BAD_REQUEST, "收件邮箱格式有误");
      addresses.add(address.trim());
    }
    var sender = senders.getIfAvailable();
    if (sender == null || host.isBlank() || !validEmail(from))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请先配置发件邮箱");
    var failed = new java.util.ArrayList<String>();
    String filename = "逾期未完成人员清单_" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")) + ".xlsx";
    for (String address : addresses) {
      try {
        var message = sender.createMimeMessage();
        var helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(address);
        helper.setSubject("【e-learning】逾期未完成人员清单");
        helper.setText("您好，附件为培训管理员选送的逾期未完成人员清单，共 " + rows + " 条培训记录。请查看附件中的培训名称和逾期情况。");
        helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(workbook),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        sender.send(message);
      } catch (jakarta.mail.MessagingException | org.springframework.mail.MailException e) {
        failed.add(address);
      }
    }
    return failed;
  }

  public boolean sendLearnerOverdue(String email, List<java.util.Map<String,Object>> rows) {
    requireConfigured();
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject("【e-learning】培训逾期提醒");
    var body = new StringBuilder().append(rows.get(0).get("name")).append("，您好：\n以下是您尚未完成的逾期事项：\n");
    for (var row : rows) {
      body.append("\n").append(row.get("trainingName")).append("：");
      if (row.get("overdueReasons") instanceof List<?> reasons)
        body.append(String.join("、", reasons.stream().map(String::valueOf).toList()));
      body.append("\n").append(publicUrl.replaceAll("/+$", "")).append("/training/").append(row.get("trainingId")).append("\n");
    }
    message.setText(body.append("\n如需继续学习或考试，请联系培训管理员。").toString());
    try {
      senders.getObject().send(message);
      return true;
    } catch (org.springframework.mail.MailException e) {
      return false;
    }
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
