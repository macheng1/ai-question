package com.aiquestion.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiquestion.common.BusinessException;
import com.aiquestion.security.*;
import com.aiquestion.service.*;
import com.aiquestion.storage.FileStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Run only against an explicitly supplied disposable MySQL database; JDBC is used solely for fixtures/assertions. */
@SpringBootTest(properties = {"storage.provider=aliyun", "spring.mail.host=localhost"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYBATIS_TEST_URL", matches = ".+aiquestion_mybatis_test.*")
class MyBatisMigrationIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    String url = System.getenv("MYBATIS_TEST_URL");
    if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:") || !url.contains("/aiquestion_mybatis_test?"))
      throw new IllegalStateException("Only an explicitly named local disposable database is allowed");
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> "root");
    registry.add("spring.datasource.password", () -> System.getenv("MYBATIS_TEST_PASSWORD"));
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired DataSource dataSource;
  @Autowired JwtTokenService tokens;
  @Autowired TrainingService trainings;
  @Autowired TrainingCopyService copies;
  @Autowired RequiredTrainingAssignmentService automaticAssignments;
  @Autowired CandidateLearningService learning;
  @Autowired MaterialLearningService reading;
  @MockitoBean com.aiquestion.integration.TrainingReminderMailer mailer;
  @MockitoBean FileStorageService storage;
  @MockitoBean TrainingFileCopyService files;

  private static final AtomicInteger SEQUENCE = new AtomicInteger(new java.security.SecureRandom().nextInt(90_000_000));
  private JdbcTemplate jdbc;
  private long owner, department, employee;
  private String phone, authorization;

  @BeforeEach void fixtures() {
    jdbc = new JdbcTemplate(dataSource);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    phone = "139" + String.format("%08d", SEQUENCE.incrementAndGet());
    owner = insert("INSERT INTO app_user(name,phone,password_hash,unit_name,role_code,created_at,updated_at) VALUES(?,?,?,'Mapper Test','ADMIN',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
        "mapper-" + suffix, "138" + String.format("%08d", SEQUENCE.incrementAndGet()), "unused-test-hash");
    department = insert("INSERT INTO organization_department(created_by,name) VALUES(?,?)", owner, "研发-" + suffix);
    employee = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'测试学员',?)", owner, department, phone);
    authorization = "Bearer " + tokens.issue(new AccountIdentity(owner, AccountType.ADMIN, owner)).accessToken();
  }

  @Test void employeeInsertReturnsGeneratedIdAndNullableFieldsWithoutChangingOtherTenants() throws Exception {
    String nextPhone = "137" + String.format("%08d", SEQUENCE.incrementAndGet());
    var saved = request(post("/management/employees").header("Authorization", authorization),
        Map.of("name", "新增学员", "phone", nextPhone, "departmentId", department));
    long id = saved.path("id").asLong();
    assertTrue(id > 0);
    assertEquals(owner, jdbc.queryForObject("SELECT created_by FROM organization_employee WHERE id=?", Long.class, id));
    assertEquals("EXTERNAL", saved.path("learnerType").asText());
    assertTrue(saved.has("email"));
    assertTrue(saved.get("email").isNull());
    assertTrue(saved.has("employeeNo"));
    var list = json.readTree(mvc.perform(get("/management/employees").header("Authorization", authorization))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertEquals(2, list.size());
    mvc.perform(put("/management/employees/999999999").header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "不存在", "phone", nextPhone, "departmentId", department, "status", "ACTIVE"))))
        .andExpect(status().isNotFound());
  }

  @Test void trainingSaveAndLearnerConfirmationPreserveOwnershipAndResponseKeys() throws Exception {
    long training = createTraining(null, false);
    var detail = trainings.get(training, owner);
    assertTrue(detail.containsKey("assessmentId"));
    assertNull(detail.get("assessmentId"));
    assertEquals(training, ((Number) detail.get("id")).longValue());
    assertThrows(BusinessException.class, () -> trainings.get(training, owner + 100000));
    var identity = request(post("/learner/session"), Map.of("type", "TRAINING", "id", training, "name", "测试学员", "phone", phone));
    assertEquals(employee, identity.path("userId").asLong());
    String learner = "Bearer " + identity.path("token").path("accessToken").asText();
    mvc.perform(get("/management/trainings").header("Authorization", learner)).andExpect(status().isForbidden());
    mvc.perform(get("/learner/me").header("Authorization", authorization)).andExpect(status().isForbidden());
    jdbc.update("UPDATE organization_employee SET status='INACTIVE' WHERE id=?", employee);
    mvc.perform(get("/learner/me").header("Authorization", learner)).andExpect(status().isUnauthorized());
  }

  @Test void copyPreservesMaterialsWithoutCreatingOrLinkingAnyExamOrPaper() throws Exception {
    long exam = createExam();
    long training = createTraining(exam, true);
    long material = material(training);
    when(files.copyMaterial(anyMap())).thenReturn("eds:training-materials/copied.pdf");
    long paperCount = jdbc.queryForObject("SELECT COUNT(*) FROM test_paper WHERE created_by=?", Long.class, owner);
    long examCount = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner);
    var copy = copies.copy(training, owner);
    long copied = ((Number) copy.get("id")).longValue();
    assertNotEquals(training, copied);
    assertNull(copy.get("assessmentId"));
    assertEquals("STANDALONE", copy.get("purpose"));
    assertEquals(examCount, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner));
    assertEquals(paperCount, jdbc.queryForObject("SELECT COUNT(*) FROM test_paper WHERE created_by=?", Long.class, owner));
    assertNull(jdbc.queryForMap("SELECT copied_assessment_id FROM training WHERE id=?", copied).get("copied_assessment_id"));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE training_id=?", Integer.class, copied));
    var copiedMaterial = jdbc.queryForMap("SELECT id,storage_key FROM training_material WHERE training_id=?", copied);
    assertNotEquals(material, ((Number) copiedMaterial.get("id")).longValue());
    assertEquals("eds:training-materials/copied.pdf", copiedMaterial.get("storage_key"));
    assertEquals(List.of(5, 5), jdbc.queryForList("SELECT required_seconds FROM training_material_page WHERE material_id=? ORDER BY page_number", Integer.class, copiedMaterial.get("id")));
    verify(storage, never()).delete(anyString());
    var secondCopy = request(post("/management/trainings/" + training + "/copy").header("Authorization", authorization), Map.of());
    assertTrue(secondCopy.path("assessmentId").isNull());
    assertNotEquals(copied, secondCopy.path("id").asLong());
    assertEquals(examCount, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner));
    assertEquals(paperCount, jdbc.queryForObject("SELECT COUNT(*) FROM test_paper WHERE created_by=?", Long.class, owner));
  }

  @Test void copyFailureRollsBackDatabaseRowsAndDeletesOnlyNewFiles() throws Exception {
    long training = createTraining(createExam(), true);
    material(training);
    material(training);
    long count = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    long exams = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner);
    when(files.copyMaterial(anyMap())).thenReturn("eds:training-materials/rollback.pdf").thenThrow(new IllegalStateException("simulated storage failure"));
    assertThrows(IllegalStateException.class, () -> copies.copy(training, owner));
    assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
    assertEquals(exams, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner));
    verify(storage).delete("eds:training-materials/rollback.pdf");
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM training_material WHERE training_id=?", Integer.class, training));
  }

  @Test void documentProgressChecksOrderHeartbeatOwnershipAndServerClock() throws Exception {
    long training = createTraining(null, false);
    long material = material(training);
    var session = learning.learning(-training, "测试学员", phone);
    String token = session.get("token").toString();
    assertEquals(employee, jdbc.queryForObject("SELECT employee_id FROM candidate_learning_session WHERE token=?", Long.class, token));
    assertThrows(BusinessException.class, () -> reading.authorize(-training, material, token, 2));
    assertThrows(BusinessException.class, () -> reading.session(-training, UUID.randomUUID().toString()));
    var initial = reading.heartbeat(-training, material, 1, token, null, 0);
    String heartbeat = initial.get("heartbeatToken").toString();
    jdbc.update("UPDATE candidate_material_progress SET last_seen=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND) WHERE material_id=?", material);
    jdbc.update("UPDATE learning_clock SET credited_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND) WHERE phone=?", phone);
    var credited = reading.heartbeat(-training, material, 1, token, heartbeat, 5);
    assertEquals(5.0, ((Number) credited.get("studiedSeconds")).doubleValue(), 0.01);
    assertEquals(true, credited.get("completed"));
    assertTrue(reading.authorize(-training, material, token, 2) > 0);
    assertThrows(BusinessException.class, () -> reading.heartbeat(-training, material, 1, token, heartbeat, 5));
    assertThrows(BusinessException.class, () -> learning.requireComplete(-training, "测试学员", phone));
  }

  @Test void failedAudienceValidationRollsBackNewTraining() throws Exception {
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    var command = trainingCommand(null, false);
    command.put("audience", Map.of("type", "USERS", "ids", List.of(999999999L)));
    mvc.perform(post("/management/trainings").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(command))).andExpect(status().isBadRequest());
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
  }

  @Test void examAttemptAnswerSubmissionAndLearnerRecordsPreserveContract() throws Exception {
    long exam = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, exam);
    long bank = insert("INSERT INTO question_bank(name,category,created_by,created_at,updated_at) VALUES('回归题库','安全',?,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", owner);
    long question = insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,'SINGLE','选择 A','A',100,?)", bank, owner);
    jdbc.update("INSERT INTO question_option(question_id,option_key,option_text,is_correct) VALUES(?,'A','正确',TRUE),(?,'B','错误',FALSE)", question, question);
    jdbc.update("INSERT INTO test_paper_question(paper_id,question_id,score) VALUES(?,?,100)", paper, question);
    jdbc.update("UPDATE test_paper SET total_score=100,pass_score=60 WHERE id=?", paper);
    var identity = request(post("/learner/session"), Map.of("type", "EXAM", "id", exam, "name", "测试学员", "phone", phone));
    String learner = "Bearer " + identity.path("token").path("accessToken").asText();
    var started = request(post("/public/exams/" + exam + "/start").header("Authorization", learner), Map.of("name", "测试学员", "phone", phone));
    long attempt = started.path("attemptId").asLong();
    assertTrue(attempt > 0);
    request(put("/public/exams/attempts/" + attempt + "/answers/" + question).header("Authorization", learner), Map.of("answer", "A"));
    var result = request(post("/public/exams/attempts/" + attempt + "/submit").header("Authorization", learner), Map.of());
    assertEquals(100, result.path("score").asInt());
    assertTrue(result.path("passed").asBoolean());
    var repeated = request(post("/public/exams/attempts/" + attempt + "/submit").header("Authorization", learner), Map.of());
    assertEquals(result.path("score"), repeated.path("score"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attempt WHERE task_id=?", Integer.class, exam));
    mvc.perform(get("/learner/exams").header("Authorization", learner)).andExpect(status().isOk());
    mvc.perform(get("/learner/mistakes").header("Authorization", learner)).andExpect(status().isOk());
    mvc.perform(get("/public/exams/" + exam).param("attemptId", Long.toString(attempt))).andExpect(status().isUnauthorized());
    String foreign = "Bearer " + tokens.issue(new AccountIdentity(employee + 999999, AccountType.EMPLOYEE, owner)).accessToken();
    mvc.perform(get("/public/exams/attempts/" + attempt + "/result").header("Authorization", foreign)).andExpect(status().isUnauthorized());
  }

  @Test void dashboardDynamicFiltersAndEmptyResultsKeepMapKeys() throws Exception {
    long training = createTraining(null, false);
    var response = mvc.perform(get("/management/dashboard").header("Authorization", authorization)
        .param("trainingId", Long.toString(training)).param("departmentId", Long.toString(department))
        .param("status", "ALL").param("tab", "learners")).andExpect(status().isOk()).andReturn();
    var data = json.readTree(response.getResponse().getContentAsString()).path("data");
    assertEquals(1, data.path("overview").path("assigned").asInt());
    var empty = mvc.perform(get("/management/dashboard").header("Authorization", authorization)
        .param("trainingId", "999999999")).andExpect(status().isOk()).andReturn();
    assertEquals(0, json.readTree(empty.getResponse().getContentAsString()).path("data").path("overview").path("assigned").asInt());
    mvc.perform(get("/management/dashboard/options").header("Authorization", authorization)).andExpect(status().isOk());
  }

  @Test void concurrentReadingHeartbeatsCreditTheSameIntervalOnlyOnce() throws Exception {
    long training = createTraining(null, false);
    long material = material(training);
    String token = learning.learning(-training, "测试学员", phone).get("token").toString();
    String heartbeat = reading.heartbeat(-training, material, 1, token, null, 0).get("heartbeatToken").toString();
    jdbc.update("UPDATE candidate_material_progress SET last_seen=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND) WHERE material_id=?", material);
    jdbc.update("UPDATE learning_clock SET credited_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND) WHERE phone=?", phone);
    var gate = new java.util.concurrent.CyclicBarrier(2);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<Boolean> heartbeatCall = () -> {
        gate.await(5, java.util.concurrent.TimeUnit.SECONDS);
        try { reading.heartbeat(-training, material, 1, token, heartbeat, 5); return true; }
        catch (BusinessException staleToken) { return false; }
      };
      var first = pool.submit(heartbeatCall);
      var second = pool.submit(heartbeatCall);
      boolean one = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
      boolean two = second.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertNotEquals(one, two, "Exactly one request may consume the heartbeat token");
      assertEquals(5.0, jdbc.queryForObject("SELECT studied_seconds FROM candidate_material_progress WHERE material_id=? AND page_number=1", Double.class, material), 0.01);
    } finally { pool.shutdownNow(); }
  }

  @Test void videoProgressSupportsExamLinkedSessionAndRejectsSkipping() throws Exception {
    long exam = createExam();
    long training = createTraining(exam, false);
    long video = insert("INSERT INTO training_video(training_id,title,storage_path,content_type) VALUES(?,'视频','eds:training-videos/test.mp4','video/mp4')", training);
    var session = learning.learning(exam, "测试学员", phone);
    String token = session.get("token").toString();
    assertEquals(employee, jdbc.queryForObject("SELECT employee_id FROM candidate_learning_session WHERE token=?", Long.class, token));
    assertThrows(BusinessException.class, () -> learning.progress(exam, video, token, 100, 100, true));
    jdbc.update("UPDATE candidate_video_progress SET baseline_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND),updated_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND) WHERE video_id=?", video);
    var progress = learning.progress(exam, video, token, 5, 100, false);
    assertEquals(5.0, ((Number) progress.get("positionSeconds")).doubleValue(), 0.01);
    assertFalse((Boolean) progress.get("completed"));
    assertThrows(BusinessException.class, () -> learning.requireComplete(exam, "测试学员", phone));
  }

  @Test void randomExamSnapshotsQuestionsOnceAndResumesSameAttempt() throws Exception {
    long exam = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, exam);
    long bank = insert("INSERT INTO question_bank(name,category,created_by,created_at,updated_at) VALUES('随机题库','安全',?,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", owner);
    for (int i = 0; i < 3; i++)
      insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,'SINGLE','随机试题','A',50,?)", bank, owner);
    jdbc.update("UPDATE test_paper SET mode='RANDOM',total_score=100 WHERE id=?", paper);
    jdbc.update("INSERT INTO test_paper_random_bank(paper_id,bank_id,single_count,single_score) VALUES(?,?,2,50)", paper, bank);
    var identity = request(post("/learner/session"), Map.of("type", "EXAM", "id", exam, "name", "测试学员", "phone", phone));
    String learner = "Bearer " + identity.path("token").path("accessToken").asText();
    var started = request(post("/public/exams/" + exam + "/start").header("Authorization", learner), Map.of("name", "测试学员", "phone", phone));
    long attempt = started.path("attemptId").asLong();
    var questions = jdbc.queryForList("SELECT question_id FROM assessment_attempt_question WHERE attempt_id=? ORDER BY sort_no", Long.class, attempt);
    assertEquals(2, questions.size());
    var resumed = request(post("/public/exams/" + exam + "/start").header("Authorization", learner), Map.of("name", "测试学员", "phone", phone));
    assertEquals(attempt, resumed.path("attemptId").asLong());
    assertEquals(questions, jdbc.queryForList("SELECT question_id FROM assessment_attempt_question WHERE attempt_id=? ORDER BY sort_no", Long.class, attempt));
  }

  @Test void departmentRestorationAssignsOnlyItsActiveEmployeesAndPreservesProgress() throws Exception {
    long training = createTraining(null, false);
    long material = material(training);
    String token = learning.learning(-training, "测试学员", phone).get("token").toString();
    reading.heartbeat(-training, material, 1, token, null, 0);
    var beforeProgress = jdbc.queryForList("SELECT * FROM candidate_material_progress WHERE material_id=?", material);
    Object assignedAt = jdbc.queryForObject("SELECT assigned_at FROM training_audience WHERE training_id=? AND employee_id=?", Object.class, training, employee);
    long otherDepartment = insert("INSERT INTO organization_department(created_by,name) VALUES(?,'其他部门')", owner);
    long second = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'第二学员',?)", owner, department, "136" + String.format("%08d", SEQUENCE.incrementAndGet()));
    long inactive = insert("INSERT INTO organization_employee(created_by,department_id,name,phone,status) VALUES(?,?,'停用学员',?,'INACTIVE')", owner, department, "136" + String.format("%08d", SEQUENCE.incrementAndGet()));
    long other = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'其他部门学员',?)", owner, otherDepartment, "136" + String.format("%08d", SEQUENCE.incrementAndGet()));
    long foreignOwner = insert("INSERT INTO app_user(name,phone,password_hash,role_code,created_at,updated_at) VALUES(UUID(),?,'unused','ADMIN',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", "138" + String.format("%08d", SEQUENCE.incrementAndGet()));
    long foreign = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'其他企业学员',?)", foreignOwner, otherDepartment, "136" + String.format("%08d", SEQUENCE.incrementAndGet()));
    request(put("/management/departments/" + department).header("Authorization", authorization), Map.of("name", "改名", "status", "ACTIVE"));
    assertEquals(List.of(employee), jdbc.queryForList("SELECT employee_id FROM training_audience WHERE training_id=? ORDER BY employee_id", Long.class, training));
    for (int cycle = 0; cycle < 2; cycle++) {
      request(put("/management/departments/" + department).header("Authorization", authorization), Map.of("name", "恢复回归", "status", "INACTIVE"));
      request(put("/management/departments/" + department).header("Authorization", authorization), Map.of("name", "恢复回归", "status", "ACTIVE"));
    }
    assertEquals(List.of(employee, second), jdbc.queryForList("SELECT employee_id FROM training_audience WHERE training_id=? ORDER BY employee_id", Long.class, training));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE training_id=? AND employee_id IN (?,?,?)", Integer.class, training, inactive, other, foreign));
    assertEquals(assignedAt, jdbc.queryForObject("SELECT assigned_at FROM training_audience WHERE training_id=? AND employee_id=?", Object.class, training, employee));
    assertEquals(beforeProgress, jdbc.queryForList("SELECT * FROM candidate_material_progress WHERE material_id=?", material));
  }

  @Test void catalogQuestionPaperAndExamManagementLifecycleUsesRealSql() throws Exception {
    long category = request(post("/management/bank-categories").header("Authorization", authorization), Map.of("name", "测试分类")).path("id").asLong();
    request(put("/management/bank-categories/" + category).header("Authorization", authorization), Map.of("name", "分类修改"));
    long bank = request(post("/management/banks").header("Authorization", authorization), Map.of("name", "题库回归", "category", "安全", "published", true)).path("id").asLong();
    var question = Map.of("bankId", bank, "questionType", "SINGLE", "stem", "测试选择", "answerText", "A",
        "options", List.of(Map.of("optionKey", "A", "optionText", "正确", "correct", true), Map.of("optionKey", "B", "optionText", "错误", "correct", false)));
    long q = request(post("/management/questions").header("Authorization", authorization), question).path("id").asLong();
    assertEquals("MEDIUM", jdbc.queryForObject("SELECT difficulty FROM question_item WHERE id=?", String.class, q));
    assertEquals(0, jdbc.queryForObject("SELECT score FROM question_item WHERE id=?", Integer.class, q));
    request(put("/management/questions/" + q).header("Authorization", authorization), question);
    long paper = request(post("/management/papers").header("Authorization", authorization), Map.of("name", "业务试卷", "mode", "FIXED", "totalScore", 100, "passScore", 60, "durationMinutes", 30)).path("id").asLong();
    request(post("/management/papers/" + paper + "/questions").header("Authorization", authorization), Map.of("questionId", q, "score", 100));
    request(patch("/management/papers/" + paper + "/status").param("value", "PUBLISHED").header("Authorization", authorization), Map.of());
    long copied = request(post("/management/papers/" + paper + "/copy").header("Authorization", authorization), Map.of("name", "试卷副本")).path("id").asLong();
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_paper_question WHERE paper_id=?", Integer.class, copied));
    request(delete("/management/papers/" + copied).header("Authorization", authorization), Map.of());
    long exam = request(post("/management/assessments").header("Authorization", authorization), Map.of("name", "业务考试", "paperId", paper, "startAt", LocalDateTime.now().plusDays(1).toString(), "endAt", LocalDateTime.now().plusDays(2).toString())).path("id").asLong();
    for (String path : List.of("/management/assessments/" + exam, "/management/assessments/" + exam + "/statistics", "/management/assessments/" + exam + "/records", "/management/assessments/" + exam + "/roster", "/management/papers/" + paper + "/questions"))
      mvc.perform(get(path).header("Authorization", authorization)).andExpect(status().isOk());
    mvc.perform(delete("/management/questions/" + q).header("Authorization", authorization)).andExpect(status().isBadRequest());
    request(delete("/management/assessments/" + exam).header("Authorization", authorization), Map.of());
    request(delete("/management/papers/" + paper).header("Authorization", authorization), Map.of());
    request(delete("/management/questions/" + q).header("Authorization", authorization), Map.of());
    request(delete("/management/banks/" + bank).header("Authorization", authorization), Map.of());
    request(delete("/management/bank-categories/" + category).header("Authorization", authorization), Map.of());
  }

  @Test void managementAndPublicReadEndpointsExecuteAgainstMigratedSchema() throws Exception {
    long training = createTraining(null, false);
    for (String path : List.of("/management/departments", "/management/employees", "/management/bank-categories", "/management/banks", "/management/papers", "/management/assessments", "/management/trainings", "/management/trainings/" + training + "/learning-records", "/management/trainings/" + training + "/materials", "/management/trainings/" + training + "/responses", "/management/trainings/" + training + "/reminders", "/management/dashboard", "/management/overview", "/auth/me", "/profile/records", "/profile/mistakes")) {
      mvc.perform(get(path).header("Authorization", authorization)).andExpect(status().isOk());
      if (path.startsWith("/management/")) mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    for (String path : List.of("/public/banks", "/public/bank-categories", "/public/self-tests", "/public/news"))
      mvc.perform(get(path)).andExpect(status().isOk());
    String learner = learnerToken(training);
    for (String path : List.of("/learner/me", "/learner/trainings", "/learner/trainings/" + training, "/learner/records", "/learner/exams", "/learner/mistakes"))
      mvc.perform(get(path).header("Authorization", learner)).andExpect(status().isOk());
  }

  @Test void noMaterialTrainingCompletionAndSurveyPersistAndRejectInvalidAnswers() throws Exception {
    var command = trainingCommand(null, false);
    command.put("surveyEnabled", true);
    command.put("questions", List.of(Map.of("id", "rating", "type", "RATING", "title", "满意度", "required", true, "options", List.of())));
    long training = request(post("/management/trainings").header("Authorization", authorization), command).path("id").asLong();
    String learner = learnerToken(training);
    var session = request(post("/public/courses/" + training + "/learning").header("Authorization", learner), Map.of("name", "测试学员", "phone", phone));
    request(post("/public/courses/" + training + "/complete").header("Authorization", learner), Map.of("token", session.path("token").asText()));
    var survey = json.readTree(mvc.perform(get("/trainings/" + training + "/survey").header("Authorization", learner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    int version = survey.path("version").asInt();
    mvc.perform(post("/trainings/" + training + "/survey").header("Authorization", learner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version", version, "answers", Map.of("rating", "9"))))).andExpect(status().isBadRequest());
    request(post("/trainings/" + training + "/survey").header("Authorization", learner), Map.of("version", version, "answers", Map.of("rating", "5")));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_survey_response WHERE training_id=?", Integer.class, training));
    mvc.perform(delete("/management/trainings/" + training).header("Authorization", authorization)).andExpect(status().isBadRequest());
  }

  @Test void restoredDepartmentRespectsScopesAndPreservesOriginalPaperBinding() throws Exception {
    long departmentCourse = createTraining(null, false);
    long optionalCourse = createTraining(null, false);
    long usersCourse = createTraining(null, false);
    long unrelatedCourse = createTraining(null, false);
    long exam = createExam();
    long excludedCourse = createTraining(exam, false);
    jdbc.update("UPDATE training SET audience_type='DEPARTMENT' WHERE id IN (?,?)", departmentCourse, unrelatedCourse);
    jdbc.update("INSERT INTO training_scope_target(training_id,target_id) VALUES(?,?),(?,?)", departmentCourse, department, unrelatedCourse, department + 999999);
    jdbc.update("UPDATE training SET requirement_type='OPTIONAL' WHERE id=?", optionalCourse);
    jdbc.update("UPDATE training SET audience_type='USERS' WHERE id=?", usersCourse);
    long participant = insert("INSERT INTO assessment_participant(task_id,candidate_name,phone,employee_id) VALUES(?,'测试学员',?,?)", exam, phone, employee);
    insert("INSERT INTO assessment_attempt(task_id,participant_id,attempt_no,status,started_at,submitted_at) VALUES(?,?,1,'SUBMITTED',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", exam, participant);
    jdbc.update("DELETE FROM training_audience WHERE training_id IN (?,?,?,?,?)", departmentCourse, optionalCourse, usersCourse, unrelatedCourse, excludedCourse);
    request(put("/management/departments/" + department).header("Authorization", authorization), Map.of("name", "范围回归", "status", "INACTIVE"));
    request(put("/management/departments/" + department).header("Authorization", authorization), Map.of("name", "范围回归", "status", "ACTIVE"));
    // Restoring the original training is permitted; a binding only excludes other trainings.
    assertEquals(List.of(departmentCourse, excludedCourse), jdbc.queryForList("SELECT training_id FROM training_audience WHERE employee_id=? ORDER BY training_id", Long.class, employee));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attempt WHERE task_id=? AND status='SUBMITTED'", Integer.class, exam));
  }

  @Test void realExamGradesAllQuestionTypesAndEnforcesRetakeAndDeadline() throws Exception {
    long exam = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, exam);
    jdbc.update("UPDATE assessment_task SET allow_retake=TRUE,max_attempts=2,duration_minutes=1 WHERE id=?", exam);
    jdbc.update("UPDATE test_paper SET total_score=30,pass_score=20 WHERE id=?", paper);
    long bank = insert("INSERT INTO question_bank(name,category,created_by,created_at,updated_at) VALUES('判分回归','安全',?,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", owner);
    List<Long> questions = new ArrayList<>();
    for (String type : List.of("SINGLE", "MULTIPLE", "TRUE_FALSE")) {
      long q = insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,?,?, ?,10,?)", bank, type, type, type.equals("SINGLE") ? "A" : type.equals("MULTIPLE") ? "A,B" : "TRUE", owner);
      questions.add(q);
      jdbc.update("INSERT INTO test_paper_question(paper_id,question_id,score) VALUES(?,?,10)", paper, q);
    }
    var identity = request(post("/learner/session"), Map.of("type", "EXAM", "id", exam, "name", "测试学员", "phone", phone));
    String learner = "Bearer " + identity.path("token").path("accessToken").asText();
    long first = request(post("/public/exams/" + exam + "/start").header("Authorization", learner), Map.of("name", "测试学员", "phone", phone)).path("attemptId").asLong();
    for (int i = 0; i < 3; i++)
      request(put("/public/exams/attempts/" + first + "/answers/" + questions.get(i)).header("Authorization", learner), Map.of("answer", List.of("A", "A,B", "TRUE").get(i)));
    var result = request(post("/public/exams/attempts/" + first + "/submit").header("Authorization", learner), Map.of());
    assertEquals(30, result.path("score").asInt());
    mvc.perform(put("/public/exams/attempts/" + first + "/answers/" + questions.get(0)).header("Authorization", learner).contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"B\"}")).andExpect(status().isBadRequest());
    long second = request(post("/public/exams/" + exam + "/start").header("Authorization", learner), Map.of("name", "测试学员", "phone", phone)).path("attemptId").asLong();
    assertNotEquals(first, second);
    jdbc.update("UPDATE assessment_attempt SET started_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 MINUTE) WHERE id=?", second);
    request(post("/public/exams/attempts/" + second + "/submit").header("Authorization", learner), Map.of());
    assertEquals(0, jdbc.queryForObject("SELECT score FROM assessment_attempt WHERE id=?", Integer.class, second));
    request(post("/public/exams/" + exam + "/start").header("Authorization", learner), Map.of("name", "测试学员", "phone", phone));
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attempt WHERE task_id=?", Integer.class, exam));
    var stats = json.readTree(mvc.perform(get("/management/assessments/" + exam + "/statistics").header("Authorization", authorization)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertEquals(1, stats.path("totalCount").asInt());
    assertEquals(0, stats.path("averageScore").asDouble());
    assertEquals(0, stats.path("passedCount").asInt());
    var allStats = json.readTree(mvc.perform(get("/management/assessments/" + exam + "/statistics").param("includeRetake", "true").header("Authorization", authorization)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertEquals(2, allStats.path("totalCount").asInt());
    assertEquals(15, allStats.path("averageScore").asDouble());
    assertEquals(1, allStats.path("passedCount").asInt());
  }

  @Test void reminderDeliveryIsIdempotentAndStoresFailureWithoutSendingRealMail() throws Exception {
    long training = createTraining(null, false);
    jdbc.update("UPDATE training SET end_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 HOUR) WHERE id=?", training);
    jdbc.update("UPDATE organization_employee SET email='learner@example.invalid' WHERE id=?", employee);
    when(mailer.configured()).thenReturn(true);
    String requestId = UUID.randomUUID().toString();
    var command = Map.of("requestId", requestId, "employeeIds", List.of(employee));
    var first = request(post("/management/trainings/" + training + "/reminders").header("Authorization", authorization), command);
    assertEquals("SENT", first.get(0).path("status").asText());
    var repeated = request(post("/management/trainings/" + training + "/reminders").header("Authorization", authorization), command);
    assertEquals("SKIPPED", repeated.get(0).path("status").asText());
    verify(mailer, times(1)).send(anyString(), anyString(), eq(training), anyString(), anyList(), anyString(), anyString());
    doThrow(new org.springframework.mail.MailSendException("test transport failure")).when(mailer).send(anyString(), anyString(), eq(training), anyString(), anyList(), anyString(), anyString());
    var failed = request(post("/management/trainings/" + training + "/reminders").header("Authorization", authorization), Map.of("requestId", UUID.randomUUID().toString(), "employeeIds", List.of(employee)));
    assertEquals("FAILED", failed.get(0).path("status").asText());
    assertEquals(List.of("SENT", "FAILED"), jdbc.queryForList("SELECT status FROM training_reminder_delivery WHERE training_id=? ORDER BY id", String.class, training));
  }

  @Test void questionTemplateImportsIntoOwnedBankAndRejectsForeignBank() throws Exception {
    long bank = request(post("/management/banks").header("Authorization", authorization), Map.of("name", "导入回归", "category", "安全")).path("id").asLong();
    byte[] template = mvc.perform(get("/management/questions/template").header("Authorization", authorization)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    assertTrue(template.length > 100);
    var file = new org.springframework.mock.web.MockMultipartFile("file", "template.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template);
    mvc.perform(multipart("/management/questions/import").file(file).param("bankId", Long.toString(bank)).header("Authorization", authorization)).andExpect(status().isOk());
    assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM question_item WHERE bank_id=?", Integer.class, bank) > 0);
    mvc.perform(multipart("/management/questions/import").file(file).param("bankId", "999999999").header("Authorization", authorization)).andExpect(status().isNotFound());
  }

  @Test void boundButUnsubmittedLearnerCannotBeAssignedToCopiedTraining() throws Exception {
    long exam = createExam();
    long original = createTraining(exam, false);
    var copy = copies.copy(original, owner);
    long copied = ((Number) copy.get("id")).longValue();
    var command = trainingCommand(samePaperExam(exam), false);
    command.put("version", copy.get("version"));
    mvc.perform(put("/management/trainings/" + copied).header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(command))).andExpect(status().isBadRequest());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE training_id=?", Integer.class, copied));
  }

  @Test void changingOnlyDeadlineDoesNotRebuildAudienceEvenBeforeLearning() throws Exception {
    long training = createTraining(null, false);
    var saved = trainings.get(training, owner);
    var assigned = jdbc.queryForObject("SELECT assigned_at FROM training_audience WHERE training_id=? AND employee_id=?", Object.class, training, employee);
    jdbc.update("UPDATE organization_employee SET status='INACTIVE' WHERE id=?", employee);
    var command = trainingCommand(null, false);
    command.put("version", saved.get("version"));
    command.put("endAt", LocalDateTime.now(ZoneOffset.UTC).plusDays(9).toString());
    request(put("/management/trainings/" + training).header("Authorization", authorization), command);
    assertEquals(assigned, jdbc.queryForObject("SELECT assigned_at FROM training_audience WHERE training_id=? AND employee_id=?", Object.class, training, employee));
  }

  @Test void originalTrainingIsEligibleButOtherTrainingAndDeletedBindingAreExcluded() throws Exception {
    long exam = createExam();
    long first = createTraining(exam, true);
    var own = json.readTree(mvc.perform(get("/management/trainings/audience-eligibility")
        .param("assessmentId", "" + exam).param("trainingId", "" + first).header("Authorization", authorization))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertEquals(0, own.size());
    long nextExam = samePaperExam(exam);
    mvc.perform(get("/management/trainings/audience-eligibility").param("assessmentId", "" + nextExam)
        .param("trainingId", "999999999").header("Authorization", authorization)).andExpect(status().isNotFound());
    trainings.delete(first, owner);
    var excluded = json.readTree(mvc.perform(get("/management/trainings/audience-eligibility")
        .param("assessmentId", "" + nextExam).header("Authorization", authorization))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertEquals(employee, excluded.get(0).asLong());
    mvc.perform(post("/management/trainings").header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(trainingCommand(nextExam, true))))
        .andExpect(status().isBadRequest());
  }

  @Test void departmentScopeExcludesBoundLearnerAndAutomaticallyAssignsNewLearnerOnlyOnce() throws Exception {
    long exam = createExam();
    long first = createTraining(exam, false);
    long secondEmployee = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'新同事',?)", owner, department, "137" + String.format("%08d", SEQUENCE.incrementAndGet()));
    var command = trainingCommand(samePaperExam(exam), false);
    command.put("audience", Map.of("type", "DEPARTMENT", "ids", List.of(department)));
    long second = request(post("/management/trainings").header("Authorization", authorization), command).path("id").asLong();
    assertEquals(List.of(secondEmployee), jdbc.queryForList("SELECT employee_id FROM training_audience WHERE training_id=?", Long.class, second));
    var third = request(post("/management/employees").header("Authorization", authorization),
        Map.of("name", "自动分配同事", "phone", "137" + String.format("%08d", SEQUENCE.incrementAndGet()), "departmentId", department));
    assertEquals(List.of(first), jdbc.queryForList("SELECT training_id FROM training_audience WHERE employee_id=?", Long.class, third.path("id").asLong()));
    var current = trainings.get(first, owner);
    var edit = trainingCommand(exam, false);
    edit.put("version", current.get("version"));
    edit.put("audience", Map.of("type", "USERS", "ids", List.of(employee)));
    request(put("/management/trainings/" + first).header("Authorization", authorization), edit);
    assertEquals(List.of(employee), jdbc.queryForList("SELECT employee_id FROM training_audience WHERE training_id=?", Long.class, first));
  }

  @Test void concurrentTrainingSavesCannotBindSameLearnerAndPaperTwice() throws Exception {
    long exam = createExam(), secondExam = samePaperExam(exam);
    var gate = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
      for (long task : List.of(exam, secondExam)) {
        var command = json.convertValue(trainingCommand(task, true), TrainingService.SaveTraining.class);
        futures.add(pool.submit(() -> {
          gate.await();
          try { trainings.save(null, command, owner); return true; }
          catch (BusinessException expected) { return false; }
        }));
      }
      gate.countDown();
      int successes = 0;
      for (var future : futures) if (future.get(15, java.util.concurrent.TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE employee_id=?", Integer.class, employee));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM learner_paper_binding WHERE owner_id=? AND employee_id=?", Integer.class, owner, employee));
    } finally { pool.shutdownNow(); }
  }

  @Test void migrationRetainsAllHistoricalSourcesAndFlagsDuplicateBindings() throws Exception {
    try (var connection = java.sql.DriverManager.getConnection(System.getenv("MYBATIS_TEST_URL"), "root", System.getenv("MYBATIS_TEST_PASSWORD"));
         var statement = connection.createStatement()) {
      statement.execute("CREATE TEMPORARY TABLE training(id BIGINT,created_by BIGINT,assessment_id BIGINT)");
      statement.execute("CREATE TEMPORARY TABLE training_audience(training_id BIGINT,employee_id BIGINT,assigned_at DATETIME(3))");
      statement.execute("CREATE TEMPORARY TABLE assessment_task(id BIGINT,paper_id BIGINT,created_by BIGINT)");
      statement.execute("CREATE TEMPORARY TABLE organization_employee(id BIGINT,created_by BIGINT)");
      statement.execute("INSERT INTO training VALUES(1,100,10),(2,100,11),(3,200,20)");
      statement.execute("INSERT INTO assessment_task VALUES(10,50,100),(11,50,100),(20,50,200)");
      statement.execute("INSERT INTO organization_employee VALUES(5,100),(6,100),(7,200),(8,200)");
      statement.execute("INSERT INTO training_audience VALUES(1,5,'2026-01-01'),(2,5,'2026-02-01'),(1,6,'2026-01-01'),(3,7,'2026-01-01'),(3,8,NULL)");
      var resource = new org.springframework.core.io.ClassPathResource("db/migration/V46__learner_paper_binding.sql");
      String sql = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).replace("CREATE TABLE ", "CREATE TEMPORARY TABLE ");
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
          new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      try (var result = statement.executeQuery("SELECT legacy_conflict,JSON_LENGTH(training_ids),JSON_CONTAINS(training_ids,'1'),JSON_CONTAINS(training_ids,'2'),DATE_FORMAT(bound_at,'%Y-%m-%d %H:%i:%s') FROM learner_paper_binding WHERE owner_id=100 AND employee_id=5")) {
        assertTrue(result.next()); assertTrue(result.getBoolean(1)); assertEquals(2,result.getInt(2));
        assertTrue(result.getBoolean(3)); assertTrue(result.getBoolean(4));
        assertEquals("2026-01-01 00:00:00", result.getString(5));
      }
      try (var result = statement.executeQuery("SELECT COUNT(*),SUM(legacy_conflict) FROM learner_paper_binding")) {
        assertTrue(result.next()); assertEquals(4,result.getInt(1)); assertEquals(1,result.getInt(2));
      }
    }
  }

  @Test void changingDeadlineAfterLearningPreservesSessionAndAssignments() throws Exception {
    long exam = createExam();
    long course = createTraining(exam, false);
    learning.learning(exam, "测试学员", phone);
    var session = jdbc.queryForObject("SELECT id FROM candidate_learning_session WHERE task_id=?", Long.class, exam);
    var assigned = jdbc.queryForObject("SELECT assigned_at FROM training_audience WHERE training_id=?", Object.class, course);
    var command = trainingCommand(exam, false);
    command.put("version", trainings.get(course, owner).get("version"));
    command.put("endAt", LocalDateTime.now(ZoneOffset.UTC).plusDays(10).toString());
    request(put("/management/trainings/" + course).header("Authorization", authorization), command);
    assertEquals(session, jdbc.queryForObject("SELECT id FROM candidate_learning_session WHERE task_id=?", Long.class, exam));
    assertEquals(assigned, jdbc.queryForObject("SELECT assigned_at FROM training_audience WHERE training_id=?", Object.class, course));
  }

  @Test void automaticAndManualAllocationSerializeWithoutDuplicatingOrDeadlocking() throws Exception {
    long exam = createExam();
    createTraining(exam, false);
    insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'第二人',?)", owner, department, "137" + String.format("%08d", SEQUENCE.incrementAndGet()));
    long secondExam = samePaperExam(exam);
    long second = createTraining(secondExam, false);
    long newcomer = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'竞争分配',?)", owner, department, "137" + String.format("%08d", SEQUENCE.incrementAndGet()));
    var body = trainingCommand(secondExam, false);
    body.put("version", trainings.get(second, owner).get("version"));
    body.put("audience", Map.of("type", "USERS", "ids", List.of(newcomer)));
    var command = json.convertValue(body, TrainingService.SaveTraining.class);
    var gate = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var manual = pool.submit(() -> { gate.await(); try { trainings.save(second, command, owner); } catch (BusinessException conflict) { assertTrue(conflict.getMessage().contains("试卷")); } return true; });
      var automatic = pool.submit(() -> { gate.await(); automaticAssignments.assignEmployee(newcomer, owner); return true; });
      gate.countDown();
      assertTrue(manual.get(15, java.util.concurrent.TimeUnit.SECONDS));
      assertTrue(automatic.get(15, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE employee_id=?", Integer.class, newcomer));
    } finally { pool.shutdownNow(); }
  }

  private long samePaperExam(long exam) {
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, exam);
    return insert("INSERT INTO assessment_task(name,paper_id,start_at,end_at,status,created_by) VALUES('同卷新考试',?,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 DAY),DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 7 DAY),'STARTED',?)", paper, owner);
  }

  private String learnerToken(long training) throws Exception {
    var identity = request(post("/learner/session"), Map.of("type", "TRAINING", "id", training, "name", "测试学员", "phone", phone));
    return "Bearer " + identity.path("token").path("accessToken").asText();
  }

  private long createTraining(Long exam, boolean future) throws Exception {
    return request(post("/management/trainings").header("Authorization", authorization), trainingCommand(exam, future)).path("id").asLong();
  }

  private Map<String, Object> trainingCommand(Long exam, boolean future) {
    var body = new HashMap<String, Object>();
    body.put("name", "映射回归培训"); body.put("description", "SQL migration");
    body.put("surveyEnabled", false); body.put("questions", List.of());
    body.put("assessmentId", exam); body.put("audience", Map.of("type", "ALL", "ids", List.of()));
    body.put("startAt", LocalDateTime.now(ZoneOffset.UTC).plusDays(future ? 1 : -1).toString());
    body.put("endAt", LocalDateTime.now(ZoneOffset.UTC).plusDays(3).toString());
    return body;
  }

  private long createExam() {
    long paper = insert("INSERT INTO test_paper(name,created_by) VALUES('映射试卷',?)", owner);
    return insert("INSERT INTO assessment_task(name,paper_id,start_at,end_at,status,created_by) VALUES('映射考试',?,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 DAY),DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 7 DAY),'STARTED',?)", paper, owner);
  }

  private long material(long training) {
    long id = insert("INSERT INTO training_material(training_id,title,material_type,storage_key,page_count) VALUES(?,'阅读材料','PDF','eds:training-materials/original.pdf',2)", training);
    jdbc.update("INSERT INTO training_material_page(material_id,page_number,required_seconds) VALUES(?,1,5),(?,2,5)", id, id);
    return id;
  }

  private long insert(String sql, Object... args) {
    var key = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
      return statement;
    }, key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  private JsonNode request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Object body) throws Exception {
    var result = mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isOk()).andReturn();
    var envelope = json.readTree(result.getResponse().getContentAsString());
    assertTrue(envelope.path("success").asBoolean(), envelope.toString());
    return envelope.path("data");
  }
}
