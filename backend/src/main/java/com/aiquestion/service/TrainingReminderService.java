package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.integration.TrainingReminderMailer;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.aiquestion.repository.TrainingReminderRepository;

@Service
public class TrainingReminderService {

    private final TrainingReminderRepository persistence;

    private final TrainingService trainings;

    private final TrainingLogService logs;

    private final TrainingReminderMailer mailer;

    public TrainingReminderService(TrainingReminderRepository persistence, TrainingService trainings, TrainingLogService logs, TrainingReminderMailer mailer) {
        this.persistence = persistence;
        this.trainings = trainings;
        this.logs = logs;
        this.mailer = mailer;
    }

    public record Recipient(long employeeId, String name, String department, String email, List<String> reasons, boolean selectable, String lastSentAt) {
    }

    public record Preview(String requestId, String trainingEndAt, String examEndAt, boolean mailConfigured, List<Recipient> recipients) {
    }

    public record SendRequest(String requestId, List<Long> employeeIds) {
    }

    public record SendResult(long employeeId, String name, String status, String message) {
    }

    static LocalDateTime time(Object value) {
        if (value instanceof Timestamp t)
            return com.aiquestion.repository.RepositoryRows.localDateTime(t);
        if (value instanceof LocalDateTime t)
            return t;
        return null;
    }

    private static String display(Object value) {
        var date = time(value);
        return date == null ? null : date.toString();
    }

    public Preview preview(long id, long owner) {
        trainings.get(id, owner);
        var course = com.aiquestion.repository.RepositoryRows.one(persistence.findTrainingDeadline(id));
        var completed = logs.completedParticipants(id, owner);
        var withoutExamReminder = new HashSet<Long>();
        if (course.get("assessment_id") != null) {
            for (var attempt : persistence.findParticipantsWithoutExamReminder(com.aiquestion.repository.RepositoryRows.nullableLong(course.get("assessment_id")))) {
                if (attempt.get("employee_id") instanceof Number employee)
                    withoutExamReminder.add(employee.longValue());
            }
        }
        var recipients = new ArrayList<Recipient>();
        var now = com.aiquestion.common.BusinessTime.now();
        // 从已分配名单开始查询，不能仅查学习日志，否则完全未开始的员工会漏掉。
        for (var row : persistence.findReminderCandidates(id, owner)) {
            long employee = ((Number) row.get("id")).longValue();
            var reasons = TrainingReminderRules.reasons(time(course.get("end_at")), completed.contains(row.get("phone") + "|" + row.get("candidate_name")), time(course.get("exam_end_at")), withoutExamReminder.contains(employee), now);
            if (reasons.isEmpty())
                continue;
            String email = Objects.toString(row.get("email"), "");
            recipients.add(new Recipient(employee, Objects.toString(row.get("name"), ""), Objects.toString(row.get("department_name"), ""), email, reasons, TrainingReminderMailer.validEmail(email), display(row.get("last_sent_at"))));
        }
        return new Preview(UUID.randomUUID().toString(), display(course.get("end_at")), display(course.get("exam_end_at")), mailer.configured(), recipients);
    }

    public List<SendResult> send(long id, long owner, SendRequest request) {
        var course = trainings.get(id, owner);
        if (request == null || request.requestId() == null || !request.requestId().matches("[a-fA-F0-9-]{36}") || request.employeeIds() == null || request.employeeIds().isEmpty() || request.employeeIds().size() > 50 || request.employeeIds().stream().anyMatch(Objects::isNull))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每次请选择 1 至 50 位员工");
        mailer.requireConfigured();
        var results = new ArrayList<SendResult>();
        for (long employee : new LinkedHashSet<>(request.employeeIds())) {
            // 每封邮件发送前重查完成状态、范围和邮箱，弹窗打开后完成的人员会跳过。
            var fresh = preview(id, owner);
            var recipient = fresh.recipients().stream().filter(r -> r.employeeId() == employee).findFirst().orElse(null);
            if (recipient == null || !recipient.selectable()) {
                results.add(new SendResult(employee, recipient == null ? "" : recipient.name(), "SKIPPED", "已完成、未逾期、不在有效范围或邮箱无效，已跳过"));
                continue;
            }
            try {
                persistence.insertDelivery(id, employee, request.requestId(), recipient.email());
            } catch (DuplicateKeyException e) {
                results.add(new SendResult(employee, recipient.name(), "SKIPPED", "本次请求已处理，未重复发送"));
                continue;
            }
            String status, message;
            try {
                mailer.send(recipient.email(), recipient.name(), id, String.valueOf(course.get("name")), recipient.reasons(), fresh.trainingEndAt() == null ? "未设置" : fresh.trainingEndAt().replace('T', ' '), fresh.examEndAt() == null ? "未关联或未设置" : fresh.examEndAt().replace('T', ' '));
                status = "SENT";
                message = "邮件服务已接收";
            } catch (org.springframework.mail.MailException e) {
                status = "FAILED";
                message = "发送失败，请检查邮件配置后重新选择发送";
            }
            persistence.updateDelivery(status, message, status, id, employee, request.requestId());
            results.add(new SendResult(employee, recipient.name(), status, message));
        }
        return results;
    }
}
