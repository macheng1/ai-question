package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.integration.TrainingReminderMailer;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TrainingReminderService {
  private final JdbcTemplate jdbc;
  private final TrainingService trainings;
  private final TrainingLogService logs;
  private final TrainingReminderMailer mailer;

  public TrainingReminderService(
      JdbcTemplate jdbc,
      TrainingService trainings,
      TrainingLogService logs,
      TrainingReminderMailer mailer) {
    this.jdbc = jdbc;
    this.trainings = trainings;
    this.logs = logs;
    this.mailer = mailer;
  }

  public record Recipient(
      long employeeId,
      String name,
      String department,
      String email,
      List<String> reasons,
      boolean selectable,
      String lastSentAt) {}

  public record Preview(
      String requestId,
      String trainingEndAt,
      String examEndAt,
      boolean mailConfigured,
      List<Recipient> recipients) {}

  public record SendRequest(String requestId, List<Long> employeeIds) {}

  public record SendResult(long employeeId, String name, String status, String message) {}

  static LocalDateTime time(Object value) {
    if (value instanceof Timestamp t) return t.toLocalDateTime();
    if (value instanceof LocalDateTime t) return t;
    return null;
  }

  private static String display(Object value) {
    var date = time(value);
    return date == null ? null : date.toString();
  }

  public Preview preview(long id, long owner) {
    trainings.get(id, owner);
    var course =
        jdbc.queryForMap(
            "SELECT t.name,t.end_at,a.end_at exam_end_at,t.assessment_id FROM training t LEFT JOIN"
                + " assessment_task a ON a.id=t.assessment_id WHERE t.id=?",
            id);
    var completed = logs.completedParticipants(id, owner);
    var passed = new HashSet<Long>();
    if (course.get("assessment_id") != null) {
      for (var attempt :
          jdbc.queryForList(
              """
SELECT p.employee_id,a.passed FROM assessment_participant p
JOIN assessment_attempt a ON a.participant_id=p.id
WHERE p.task_id=? AND a.id=(SELECT MAX(last.id) FROM assessment_attempt last WHERE last.participant_id=p.id)
AND a.status='SUBMITTED' AND a.passed=TRUE
""",
              course.get("assessment_id"))) {
        if (attempt.get("employee_id") instanceof Number employee) passed.add(employee.longValue());
      }
    }
    var recipients = new ArrayList<Recipient>();
    var now = LocalDateTime.now();
    // 从已分配名单开始查询，不能仅查学习日志，否则完全未开始的员工会漏掉。
    for (var row :
        jdbc.queryForList(
            """
SELECT e.id,e.name,e.email,a.phone,a.candidate_name,a.department_name,
(SELECT MAX(r.sent_at) FROM training_reminder_delivery r WHERE r.training_id=a.training_id AND r.employee_id=e.id AND r.status='SENT') last_sent_at
FROM training_audience a JOIN organization_employee e ON e.id=a.employee_id
WHERE a.training_id=? AND e.created_by=? AND e.status='ACTIVE'
ORDER BY e.id
""",
            id,
            owner)) {
      long employee = ((Number) row.get("id")).longValue();
      var reasons =
          TrainingReminderRules.reasons(
              time(course.get("end_at")),
              completed.contains(row.get("phone") + "|" + row.get("candidate_name")),
              time(course.get("exam_end_at")),
              passed.contains(employee),
              now);
      if (reasons.isEmpty()) continue;
      String email = Objects.toString(row.get("email"), "");
      recipients.add(
          new Recipient(
              employee,
              Objects.toString(row.get("name"), ""),
              Objects.toString(row.get("department_name"), ""),
              email,
              reasons,
              TrainingReminderMailer.validEmail(email),
              display(row.get("last_sent_at"))));
    }
    return new Preview(
        UUID.randomUUID().toString(),
        display(course.get("end_at")),
        display(course.get("exam_end_at")),
        mailer.configured(),
        recipients);
  }

  public List<SendResult> send(long id, long owner, SendRequest request) {
    var course = trainings.get(id, owner);
    if (request == null
        || request.requestId() == null
        || !request.requestId().matches("[a-fA-F0-9-]{36}")
        || request.employeeIds() == null
        || request.employeeIds().isEmpty()
        || request.employeeIds().size() > 50
        || request.employeeIds().stream().anyMatch(Objects::isNull))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "每次请选择 1 至 50 位员工");
    mailer.requireConfigured();
    var results = new ArrayList<SendResult>();
    for (long employee : new LinkedHashSet<>(request.employeeIds())) {
      // 每封邮件发送前重查完成状态、范围和邮箱，弹窗打开后完成的人员会跳过。
      var fresh = preview(id, owner);
      var recipient =
          fresh.recipients().stream()
              .filter(r -> r.employeeId() == employee)
              .findFirst()
              .orElse(null);
      if (recipient == null || !recipient.selectable()) {
        results.add(
            new SendResult(
                employee,
                recipient == null ? "" : recipient.name(),
                "SKIPPED",
                "已完成、未逾期、不在有效范围或邮箱无效，已跳过"));
        continue;
      }
      try {
        jdbc.update(
            "INSERT INTO"
                + " training_reminder_delivery(training_id,employee_id,request_id,email,status)"
                + " VALUES(?,?,?,?,'SENDING')",
            id,
            employee,
            request.requestId(),
            recipient.email());
      } catch (DuplicateKeyException e) {
        results.add(new SendResult(employee, recipient.name(), "SKIPPED", "本次请求已处理，未重复发送"));
        continue;
      }
      String status, message;
      try {
        mailer.send(
            recipient.email(),
            recipient.name(),
            id,
            String.valueOf(course.get("name")),
            recipient.reasons(),
            fresh.trainingEndAt() == null ? "未设置" : fresh.trainingEndAt().replace('T', ' '),
            fresh.examEndAt() == null ? "未关联或未设置" : fresh.examEndAt().replace('T', ' '));
        status = "SENT";
        message = "邮件服务已接收";
      } catch (org.springframework.mail.MailException e) {
        status = "FAILED";
        message = "发送失败，请检查邮件配置后重新选择发送";
      }
      jdbc.update(
          "UPDATE training_reminder_delivery SET status=?,message=?,sent_at=CASE WHEN ?='SENT' THEN"
              + " CURRENT_TIMESTAMP(3) ELSE NULL END WHERE training_id=? AND employee_id=? AND"
              + " request_id=?",
          status,
          message,
          status,
          id,
          employee,
          request.requestId());
      results.add(new SendResult(employee, recipient.name(), status, message));
    }
    return results;
  }
}
