package com.aiquestion.integration;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

/** 邮件错误分类；不把服务器原始响应中的凭据、地址或邮件内容返回给页面。 */
public final class MailFailureDetails {
  private MailFailureDetails() {}
  private static final Pattern SMTP_CODE = Pattern.compile("(?m)(?:^|\\s)([45]\\d{2})(?:[ -]|$)");

  public static List<Throwable> causes(Throwable error) {
    var result = new ArrayList<Throwable>();
    var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    var pending = new ArrayDeque<Throwable>();
    pending.add(error);
    while (!pending.isEmpty() && result.size() < 32) {
      var current = pending.removeFirst();
      if (!seen.add(current)) continue;
      result.add(current);
      if (current.getCause() != null) pending.add(current.getCause());
      if (current instanceof jakarta.mail.MessagingException mail && mail.getNextException() != null)
        pending.add(mail.getNextException());
      if (current instanceof MailSendException mail) pending.addAll(mail.getFailedMessages().values());
    }
    return result;
  }

  public static String describe(Throwable error) {
    var causes = causes(error);
    if (causes.stream().anyMatch(e -> e instanceof MailAuthenticationException || e instanceof jakarta.mail.AuthenticationFailedException))
      return "邮件服务器认证失败，请检查发件账号及授权码";
    if (causes.stream().anyMatch(e -> e instanceof java.net.SocketTimeoutException))
      return "邮件连接、读取或写入超时（超时设置为60秒），发送结果可能尚未确认，请核对收件情况";
    if (causes.stream().anyMatch(e -> e instanceof java.net.UnknownHostException))
      return "无法解析邮件服务器地址，请检查邮件服务器配置和DNS";
    if (causes.stream().anyMatch(e -> e instanceof javax.net.ssl.SSLException))
      return "邮件服务器TLS连接失败，请检查端口、加密方式和证书";
    if (causes.stream().anyMatch(e -> e instanceof java.net.ConnectException))
      return "无法连接邮件服务器，请检查端口、网络和防火墙";
    for (var cause : causes) {
      var matcher = SMTP_CODE.matcher(Objects.toString(cause.getMessage(), ""));
      if (matcher.find()) {
        String code = matcher.group(1);
        String hint = switch (code) {
          case "535", "534", "530" -> "请检查发件账号认证及授权码";
          case "550", "551", "553" -> "请检查收件地址、发件权限和反垃圾策略";
          case "552" -> "请检查收件箱容量及附件大小限制";
          case "421", "450", "451", "452", "454" -> "服务暂不可用或触发限流，请稍后核对并重试";
          default -> "请检查邮件服务器策略及收发件配置";
        };
        return "邮件服务器拒绝发送（SMTP " + code + "），" + hint;
      }
    }
    if (causes.stream().anyMatch(e -> e instanceof jakarta.mail.internet.AddressException)) return "邮箱地址格式有误";
    return "邮件发送失败（" + causes.get(causes.size() - 1).getClass().getSimpleName() + "），请联系管理员查看邮件发送日志";
  }
}
