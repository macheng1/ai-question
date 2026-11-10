package com.aiquestion.service;

import com.aiquestion.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingAudienceService {
  private final JdbcTemplate jdbc;

  public TrainingAudienceService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Scope(String type, List<Long> ids) {}

  public Scope get(long id, long user) {
    requireOwner(id, user);
    return new Scope(
        jdbc.queryForObject("SELECT audience_type FROM training WHERE id=?", String.class, id),
        jdbc.queryForList(
            "SELECT target_id FROM training_scope_target WHERE training_id=? ORDER BY target_id",
            Long.class,
            id));
  }

  private void requireOwner(long id, long user) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM training WHERE id=? AND created_by=?", Integer.class, id, user)
        == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
  }

  @Transactional
  public void save(long id, long user, Scope scope) {
    requireOwner(id, user);
    var course = jdbc.queryForMap("SELECT * FROM training WHERE id=? FOR UPDATE", id);
    if (scope == null
        || scope.type() == null
        || !Set.of("ALL", "DEPARTMENT", "USERS").contains(scope.type())
        || scope.ids() == null
        || scope.ids().stream().anyMatch(Objects::isNull))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "学习范围无效");
    Set<Long> requestedIds = new TreeSet<>(scope.ids());
    if (scope.type().equals("ALL") && !requestedIds.isEmpty())
      throw new BusinessException(ErrorCode.BAD_REQUEST, "全部员工范围无需指定部门或人员");
    var existing = get(id, user);
    boolean unchanged =
        Objects.equals(existing.type(), scope.type())
            && requestedIds.equals(new TreeSet<>(existing.ids()));
    boolean locked =
        jdbc.queryForObject(
                    "SELECT COUNT(*) FROM candidate_learning_session s JOIN training t ON"
                        + " (t.assessment_id=s.task_id OR t.id=s.training_id) WHERE t.id=?",
                    Integer.class,
                    id)
                > 0
            || (course.get("assessment_id") instanceof Number task
                && jdbc.queryForObject(
                        "SELECT COUNT(*) FROM assessment_participant WHERE task_id=?",
                        Integer.class,
                        task.longValue())
                    > 0);
    if (locked) {
      if (unchanged) return;
      throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习或考试记录，不能修改学习范围");
    }
    var employees =
        jdbc.queryForList(
            "SELECT e.id,e.name,e.phone,e.department_id,d.name department_name FROM"
                + " organization_employee e JOIN organization_department d ON d.id=e.department_id"
                + " AND d.created_by=e.created_by WHERE e.created_by=? AND e.status='ACTIVE' AND"
                + " d.status='ACTIVE'",
            user);
    Set<Long> ids = new HashSet<>(scope.ids());
    if (Set.of("DEPARTMENT", "USERS").contains(scope.type())) {
      var allowed =
          scope.type().equals("DEPARTMENT")
              ? jdbc.queryForList(
                  "SELECT id FROM organization_department WHERE created_by=? AND status='ACTIVE'",
                  Long.class,
                  user)
              : employees.stream().map(e -> ((Number) e.get("id")).longValue()).toList();
      if (ids.isEmpty() || !allowed.containsAll(ids))
        throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择本企业有效的部门或员工");
    }
    var selected =
        employees.stream()
            .filter(
                e ->
                    scope.type().equals("ALL")
                        || scope.type().equals("DEPARTMENT")
                            && ids.contains(((Number) e.get("department_id")).longValue())
                        || scope.type().equals("USERS")
                            && ids.contains(((Number) e.get("id")).longValue()))
            .toList();
    if (selected.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "选择范围内没有在用员工");
    var assignedTimes = new HashMap<Long, Object>();
    for (var previous :
        jdbc.queryForList(
            "SELECT employee_id,assigned_at FROM training_audience WHERE training_id=?", id))
      assignedTimes.put(
          ((Number) previous.get("employee_id")).longValue(), previous.get("assigned_at"));
    jdbc.update("DELETE FROM training_audience WHERE training_id=?", id);
    jdbc.update("DELETE FROM training_scope_target WHERE training_id=?", id);
    jdbc.update("UPDATE training SET audience_type=? WHERE id=?", scope.type(), id);
    for (long target : ids)
      jdbc.update(
          "INSERT INTO training_scope_target(training_id,target_id) VALUES(?,?)", id, target);
    for (var e : selected)
      jdbc.update(
          "INSERT INTO"
              + " training_audience(training_id,employee_id,candidate_name,phone,department_id,department_name,assigned_at)"
              + " VALUES(?,?,?,?,?,?,COALESCE(?,CURRENT_TIMESTAMP(3)))",
          id,
          e.get("id"),
          e.get("name"),
          e.get("phone"),
          e.get("department_id"),
          e.get("department_name"),
          assignedTimes.get(((Number) e.get("id")).longValue()));
  }

  public static void require(JdbcTemplate jdbc, long training, String name, String phone) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM training_audience WHERE training_id=? AND candidate_name=? AND"
                + " phone=?",
            Integer.class,
            training,
            name.trim(),
            phone.trim())
        == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "你不在本培训的学习范围内");
  }

  public static void requireTask(JdbcTemplate jdbc, long task, String name, String phone) {
    Integer notStarted = jdbc.queryForObject(
        "SELECT COUNT(*) FROM training WHERE " + (task > 0 ? "assessment_id=?" : "id=?")
            + " AND start_at>CURRENT_TIMESTAMP(3)", Integer.class, Math.abs(task));
    if (notStarted != null && notStarted > 0)
      throw new BusinessException(ErrorCode.BAD_REQUEST, "培训尚未开始");
    if (task < 0
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM training WHERE id=? AND assessment_id IS NOT NULL",
                Integer.class,
                -task)
            > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "请从关联考试进入配套培训");
    for (long id :
        jdbc.queryForList(
            "SELECT id FROM training WHERE " + (task > 0 ? "assessment_id=?" : "id=?"),
            Long.class,
            Math.abs(task))) require(jdbc, id, name, phone);
  }
}
