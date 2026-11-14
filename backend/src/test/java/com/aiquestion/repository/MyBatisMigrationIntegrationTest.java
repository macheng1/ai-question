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
@SpringBootTest(properties = {"storage.provider=aliyun", "spring.mail.host=localhost", "training.preview.enabled=false"})
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

  @Autowired QuestionItemRepository questionItems;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired DataSource dataSource;
  @Autowired JwtTokenService tokens;
  @Autowired TrainingService trainings;
  @Autowired TrainingCopyService copies;
  @Autowired RequiredTrainingAssignmentService automaticAssignments;
  @Autowired CandidateLearningService learning;
  @Autowired MaterialLearningService reading;
  @Autowired TrainingMaterialService trainingMaterials;
  @Autowired MaterialPreviewService previews;
  @Autowired org.apache.ibatis.session.SqlSessionFactory sqlSessions;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired TrainingLogService trainingLogs;
  @Autowired LearnerRecordService learnerRecords;
  @Autowired PublicExamService publicExams;
  @Autowired AssessmentRosterService assessmentRoster;
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
    var secondCopy = request(post("/management/trainings/" + training + "/copy").header("Authorization", authorization), Map.of("sourceVersion", trainings.get(training, owner).get("version"), "training", trainingCommand(null, true)));
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
    assertEquals(credited, reading.heartbeat(-training, material, 1, token, heartbeat, 5));
    assertThrows(BusinessException.class, () -> reading.heartbeat(-training, material, 1, token, heartbeat, 4));
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

  @Test void mistakesIncludeUnansweredSnapshotQuestionsAndOnlyLatestVisibleSubmission() {
    long exam = createExam();
    jdbc.update("UPDATE assessment_task SET show_result_immediately=1 WHERE id=?", exam);
    long bank = insert("INSERT INTO question_bank(name,category,created_by,created_at,updated_at) VALUES('错题回归','安全',?,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", owner);
    long correct = insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,'SINGLE','正确题','A',25,?)", bank, owner);
    long wrong = insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,'SINGLE','错误题','A',25,?)", bank, owner);
    long skipped = insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,'MULTIPLE','未答题','A,B',25,?)", bank, owner);
    long cleared = insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,'JUDGE','清空题','TRUE',25,?)", bank, owner);
    long participant = insert("INSERT INTO assessment_participant(task_id,candidate_name,phone,employee_id) VALUES(?,'测试学员',?,?)", exam, phone, employee);
    long attempt = insert("INSERT INTO assessment_attempt(task_id,participant_id,attempt_no,status,started_at) VALUES(?,?,1,'IN_PROGRESS',CURRENT_TIMESTAMP(3))", exam, participant);
    for (long q : List.of(correct, wrong, skipped, cleared))
      jdbc.update("INSERT INTO assessment_attempt_question(attempt_id,question_id,score) VALUES(?,?,25)", attempt, q);
    jdbc.update("INSERT INTO assessment_answer(attempt_id,question_id,answer_text,is_correct,score) VALUES(?,?,'A',1,25),(?,?,'B',0,0),(?,?,'',0,0)", attempt, correct, attempt, wrong, attempt, cleared);
    var identity = new AccountIdentity(employee, AccountType.EMPLOYEE, owner);
    assertTrue(learnerRecords.mistakes(identity).isEmpty());
    jdbc.update("UPDATE assessment_attempt SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP(3) WHERE id=?", attempt);
    var mistakes = learnerRecords.mistakes(identity);
    assertEquals(3, mistakes.size());
    assertEquals(Set.of(wrong, skipped, cleared), mistakes.stream().map(r -> ((Number)r.get("questionId")).longValue()).collect(java.util.stream.Collectors.toSet()));
    assertEquals("", mistakes.stream().filter(r -> ((Number)r.get("questionId")).longValue()==skipped).findFirst().orElseThrow().get("userAnswer"));
    assertEquals(3, mistakes.stream().map(r -> r.get("id")).distinct().count());
    jdbc.update("UPDATE assessment_task SET show_result_immediately=0 WHERE id=?", exam);
    assertTrue(learnerRecords.mistakes(identity).isEmpty());
    jdbc.update("UPDATE assessment_task SET show_result_immediately=1 WHERE id=?", exam);
    long retry = insert("INSERT INTO assessment_attempt(task_id,participant_id,attempt_no,status,started_at) VALUES(?,?,2,'IN_PROGRESS',CURRENT_TIMESTAMP(3))", exam, participant);
    jdbc.update("INSERT INTO assessment_attempt_question(attempt_id,question_id,score) VALUES(?,?,100)", retry, correct);
    assertEquals(3, learnerRecords.mistakes(identity).size());
    jdbc.update("UPDATE assessment_attempt SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP(3) WHERE id=?", retry);
    var latest = learnerRecords.mistakes(identity);
    assertEquals(1, latest.size());
    assertEquals(correct, ((Number)latest.get(0).get("questionId")).longValue());
    assertEquals("", latest.get(0).get("userAnswer"));
    assertTrue(learnerRecords.mistakes(new AccountIdentity(employee, AccountType.EMPLOYEE, owner + 99999)).isEmpty());
  }

  @Test void differentCandidatesCanStartTheSameExamBeforeFirstCommit() throws Exception {
    long exam=createExam();
    var entered=new java.util.concurrent.CountDownLatch(1);
    var release=new java.util.concurrent.CountDownLatch(1);
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first=pool.submit(()->readingTransaction().execute(status->{
        var result=publicExams.start(exam,new com.aiquestion.controller.PublicExamController.CandidateCommand("测试学员",phone));
        entered.countDown();awaitRelease(release);return result;
      }));
      assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
      var second=pool.submit(()->publicExams.start(exam,new com.aiquestion.controller.PublicExamController.CandidateCommand("另一学员","137"+String.format("%08d",SEQUENCE.incrementAndGet()))));
      assertNotNull(second.get(3,java.util.concurrent.TimeUnit.SECONDS));
      release.countDown();first.get(5,java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attempt WHERE task_id=?",Integer.class,exam));
    } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
  }

  @Test void differentCandidatesCanSaveAnswersBeforeFirstCommit() throws Exception {
    long exam=createExam(), question=examQuestion(exam);
    long firstAttempt=startAttempt(exam,"测试学员",phone), secondAttempt=startAttempt(exam,"另一学员","137"+String.format("%08d",SEQUENCE.incrementAndGet()));
    var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first=pool.submit(()->readingTransaction().execute(status->{
        publicExams.answer(firstAttempt,question,new com.aiquestion.controller.PublicExamController.AnswerCommand("A"));
        entered.countDown();awaitRelease(release);return true;
      }));
      assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
      var second=pool.submit(()->publicExams.answer(secondAttempt,question,new com.aiquestion.controller.PublicExamController.AnswerCommand("B")));
      assertNotNull(second.get(3,java.util.concurrent.TimeUnit.SECONDS));
      release.countDown();first.get(5,java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM assessment_answer WHERE question_id=?",Integer.class,question));
    } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
  }

  @Test void duplicateStartsSerializePerCandidateAndRespectRetakeLimit() throws Exception {
    long exam=createExam(), question=examQuestion(exam);
    jdbc.update("UPDATE assessment_task SET allow_retake=1,max_attempts=2 WHERE id=?",exam);
    for(int round=1;round<=2;round++) {
      var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
      var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        var first=pool.submit(()->readingTransaction().execute(status->{
          long attempt=startAttempt(exam,"测试学员",phone);entered.countDown();awaitRelease(release);return attempt;
        }));
        assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
        var second=pool.submit(()->startAttempt(exam,"测试学员",phone));
        assertThrows(java.util.concurrent.TimeoutException.class,()->second.get(300,java.util.concurrent.TimeUnit.MILLISECONDS));
        release.countDown();long attempt=first.get(5,java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(attempt,second.get(5,java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(round,jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attempt WHERE task_id=?",Integer.class,exam));
        publicExams.answer(attempt,question,new com.aiquestion.controller.PublicExamController.AnswerCommand("A"));
        publicExams.submit(attempt);
      } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
    }
    publicExams.start(exam,new com.aiquestion.controller.PublicExamController.CandidateCommand("测试学员",phone));
    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM assessment_attempt WHERE task_id=?",Integer.class,exam));
    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM assessment_participant WHERE task_id=?",Integer.class,exam));
  }

  @Test void answerCannotOverwriteConcurrentSubmission() throws Exception {
    long exam=createExam(),question=examQuestion(exam),attempt=startAttempt(exam,"测试学员",phone);
    publicExams.answer(attempt,question,new com.aiquestion.controller.PublicExamController.AnswerCommand("A"));
    var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first=pool.submit(()->readingTransaction().execute(status->{
        publicExams.submit(attempt);entered.countDown();awaitRelease(release);return true;
      }));
      assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
      var second=pool.submit(()->publicExams.answer(attempt,question,new com.aiquestion.controller.PublicExamController.AnswerCommand("B")));
      assertThrows(java.util.concurrent.TimeoutException.class,()->second.get(300,java.util.concurrent.TimeUnit.MILLISECONDS));
      release.countDown();first.get(5,java.util.concurrent.TimeUnit.SECONDS);
      var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->second.get(5,java.util.concurrent.TimeUnit.SECONDS));
      assertInstanceOf(BusinessException.class,failure.getCause());
      assertEquals(100,jdbc.queryForObject("SELECT score FROM assessment_attempt WHERE id=?",Integer.class,attempt));
    } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
  }

  @Test void endingExamWaitsForAnswerAndGradesCommittedScore() throws Exception {
    long exam=createExam(),question=examQuestion(exam),training=createTraining(exam,false);
    long attempt=startAttempt(exam,"测试学员",phone);
    var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first=pool.submit(()->readingTransaction().execute(status->{
        publicExams.answer(attempt,question,new com.aiquestion.controller.PublicExamController.AnswerCommand("A"));
        entered.countDown();awaitRelease(release);return true;
      }));
      assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
      var second=pool.submit(()->trainings.endExam(training,owner));
      assertThrows(java.util.concurrent.TimeoutException.class,()->second.get(300,java.util.concurrent.TimeUnit.MILLISECONDS));
      release.countDown();first.get(5,java.util.concurrent.TimeUnit.SECONDS);second.get(5,java.util.concurrent.TimeUnit.SECONDS);
      assertEquals("SUBMITTED",jdbc.queryForObject("SELECT status FROM assessment_attempt WHERE id=?",String.class,attempt));
      assertEquals(100,jdbc.queryForObject("SELECT score FROM assessment_attempt WHERE id=?",Integer.class,attempt));
      assertThrows(BusinessException.class,()->publicExams.answer(attempt,question,new com.aiquestion.controller.PublicExamController.AnswerCommand("B")));
    } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
  }

  @Test void differentLearnersCanEnterLinkedAndStandaloneTrainingConcurrently() throws Exception {
    String otherPhone="137"+String.format("%08d",SEQUENCE.incrementAndGet());
    insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'并发学员',?)",owner,department,otherPhone);
    for(Long exam:Arrays.asList(null,createExam())) {
      long training=createTraining(exam,false),entry=exam==null?-training:exam;
      var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
      var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
      try {
        var first=pool.submit(()->readingTransaction().execute(status->{
          var result=learning.learning(entry,"测试学员",phone);entered.countDown();awaitRelease(release);return result;
        }));
        assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
        var second=pool.submit(()->learning.learning(entry,"并发学员",otherPhone));
        assertNotNull(second.get(3,java.util.concurrent.TimeUnit.SECONDS).get("token"));
        release.countDown();first.get(5,java.util.concurrent.TimeUnit.SECONDS);
      } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
    }
  }

  private long examQuestion(long exam) {
    long paper=jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?",Long.class,exam);
    long bank=insert("INSERT INTO question_bank(name,category,created_by,created_at,updated_at) VALUES('并发题库','安全',?,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",owner);
    long question=insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,'SINGLE','选择 A','A',100,?)",bank,owner);
    jdbc.update("INSERT INTO test_paper_question(paper_id,question_id,score) VALUES(?,?,100)",paper,question);
    jdbc.update("UPDATE test_paper SET total_score=100,pass_score=60 WHERE id=?",paper);
    return question;
  }

  private long startAttempt(long exam,String name,String candidatePhone) {
    return ((Number)publicExams.start(exam,new com.aiquestion.controller.PublicExamController.CandidateCommand(name,candidatePhone)).data().get("attemptId")).longValue();
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

  @Test void differentLearnersCanSaveTheSamePageBeforeAnotherLearnerCommits() throws Exception {
    String otherPhone = "137" + String.format("%08d", SEQUENCE.incrementAndGet());
    long other = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'并发学员',?)", owner, department, otherPhone);
    var command = trainingCommand(null, false);
    command.put("audience", Map.of("type", "USERS", "ids", List.of(employee, other)));
    long training = request(post("/management/trainings").header("Authorization", authorization), command).path("id").asLong();
    long material = material(training);
    String firstToken = learning.learning(-training, "测试学员", phone).get("token").toString();
    String secondToken = learning.learning(-training, "并发学员", otherPhone).get("token").toString();
    var saved = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> readingTransaction()
          .execute(status -> {
            var result = reading.heartbeat(-training, material, 1, firstToken, null, 0);
            saved.countDown();
            awaitRelease(release);
            return result;
          }));
      assertTrue(saved.await(5, java.util.concurrent.TimeUnit.SECONDS));
      var second = pool.submit(() -> reading.heartbeat(-training, material, 1, secondToken, null, 0));
      try {
        assertNotNull(second.get(3, java.util.concurrent.TimeUnit.SECONDS).get("heartbeatToken"),
            "Another learner must finish while the first learner's transaction remains open");
      } catch (java.util.concurrent.TimeoutException e) {
        throw new AssertionError("Concurrent learner blocked: " + jdbc.queryForList(
            "SELECT OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_STATUS,LOCK_DATA FROM performance_schema.data_locks WHERE OBJECT_SCHEMA=DATABASE()"), e);
      }
      release.countDown();
      first.get(5, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM candidate_material_progress WHERE material_id=?", Integer.class, material));
    } finally { release.countDown(); pool.shutdownNow(); pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS); }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"timing", "delete"})
  void materialEditsWaitForReadingAndThenRejectNewlyCommittedProgress(String operation) throws Exception {
    long training = createTraining(null, false);
    long material = material(training);
    String token = learning.learning(-training, "测试学员", phone).get("token").toString();
    var saved = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var editing = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> readingTransaction()
          .execute(status -> {
            reading.heartbeat(-training, material, 1, token, null, 0);
            saved.countDown();
            awaitRelease(release);
            return true;
          }));
      assertTrue(saved.await(5, java.util.concurrent.TimeUnit.SECONDS));
      var edit = pool.submit(() -> {
        editing.countDown();
        return assertThrows(BusinessException.class, () -> {
          if ("delete".equals(operation)) trainingMaterials.delete(training, material, owner);
          else trainingMaterials.timings(training, material, owner, List.of(
              new TrainingMaterialService.PageTime(1, 30), new TrainingMaterialService.PageTime(2, 30)));
        });
      });
      assertTrue(editing.await(5, java.util.concurrent.TimeUnit.SECONDS));
      assertThrows(java.util.concurrent.TimeoutException.class, () -> edit.get(300, java.util.concurrent.TimeUnit.MILLISECONDS));
      release.countDown();
      first.get(5, java.util.concurrent.TimeUnit.SECONDS);
      assertTrue(edit.get(5, java.util.concurrent.TimeUnit.SECONDS).getMessage().contains("已有学习记录"));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM candidate_material_progress WHERE material_id=?", Integer.class, material));
      assertEquals(List.of(5, 5), jdbc.queryForList("SELECT required_seconds FROM training_material_page WHERE material_id=? ORDER BY page_number", Integer.class, material));
    } finally { release.countDown(); pool.shutdownNow(); pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS); }
  }

  private org.springframework.transaction.support.TransactionTemplate readingTransaction() {
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    // Keep the heartbeat transaction open using the same isolation as the production entry point.
    tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    return tx;
  }

  private static void awaitRelease(java.util.concurrent.CountDownLatch release) {
    try {
      if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test transaction was not released");
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
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
      assertTrue(one && two, "A lost response may be retried successfully without crediting twice");
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

  @Test void videoRetryReturnsAcknowledgementWithoutChangingSavedTime() throws Exception {
    long training=createTraining(null,false);
    long video=insert("INSERT INTO training_video(training_id,title,storage_path,content_type) VALUES(?,'重试视频','private.mp4','video/mp4')",training);
    String token=learning.learning(-training,"测试学员",phone).get("token").toString();
    learning.progress(-training,video,token,0,100,false,"start");
    jdbc.update("UPDATE candidate_video_progress SET baseline_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND),updated_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND) WHERE video_id=?",video);
    jdbc.update("UPDATE learning_clock SET credited_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 6 SECOND) WHERE phone=?",phone);
    var first=learning.progress(-training,video,token,3,100,false,"batch-1");
    var before=jdbc.queryForMap("SELECT studied_seconds,updated_at FROM candidate_video_progress WHERE video_id=?",video);
    assertEquals(3.0,((Number)before.get("studied_seconds")).doubleValue(),0.01);
    assertEquals(first,learning.progress(-training,video,token,3,100,false,"batch-1"));
    assertEquals(before,jdbc.queryForMap("SELECT studied_seconds,updated_at FROM candidate_video_progress WHERE video_id=?",video));
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
    var emptyStats = statisticsDetail(exam, false);
    assertEquals(30, emptyStats.path("overview").path("totalScore").asInt());
    assertEquals(20, emptyStats.path("overview").path("passScore").asInt());
    assertEquals(0, emptyStats.path("overview").path("participantCount").asInt());
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
    var inProgressStats = statisticsDetail(exam, false);
    assertEquals(1, inProgressStats.path("overview").path("participantCount").asInt());
    assertEquals(30, inProgressStats.path("overview").path("averageScore").asInt());

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
    jdbc.update("UPDATE assessment_attempt SET started_at='2026-11-12 10:00:00',submitted_at='2026-11-12 10:02:00' WHERE id=?", first);
    jdbc.update("UPDATE assessment_attempt SET started_at='2026-11-12 10:03:00',submitted_at='2026-11-12 10:06:00' WHERE id=?", second);
    var detail = statisticsDetail(exam, false);
    assertEquals(1, detail.path("overview").path("participantCount").asInt());
    assertEquals(0, detail.path("overview").path("averageScore").asDouble());
    assertEquals(0, detail.path("overview").path("passRate").asDouble());
    assertEquals(180, detail.path("overview").path("averageDurationSeconds").asDouble());
    assertEquals(1, detail.path("submissionTrend").size());
    assertEquals(3, detail.path("typeScore").size());
    assertEquals(3, detail.path("difficultQuestions").size());
    for (var type : detail.path("typeScore")) assertEquals(0, type.path("correctRate").asDouble());
    for (var question : detail.path("difficultQuestions")) assertEquals(0, question.path("correctRate").asDouble());
    var allDetail = statisticsDetail(exam, true);
    assertEquals(2, allDetail.path("overview").path("participantCount").asInt());
    assertEquals(15, allDetail.path("overview").path("averageScore").asDouble());
    assertEquals(50, allDetail.path("overview").path("passRate").asDouble());
    assertEquals(30, allDetail.path("overview").path("highestScore").asDouble());
    assertEquals(0, allDetail.path("overview").path("lowestScore").asDouble());
    assertEquals(150, allDetail.path("overview").path("averageDurationSeconds").asDouble());
    assertEquals(2, allDetail.path("submissionTrend").size());
    assertEquals(2, allDetail.path("passDistribution").size());
    assertEquals(2, allDetail.path("scoreDistribution").size());
    for (var type : allDetail.path("typeScore")) assertEquals(50, type.path("correctRate").asDouble());
    for (var question : allDetail.path("difficultQuestions")) assertEquals(50, question.path("correctRate").asDouble());
    long otherTask = insert("INSERT INTO assessment_task(name,paper_id,created_by,start_at,end_at) VALUES('另一期',?,?,CURRENT_TIMESTAMP(3),DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 1 DAY))", paper, owner);
    assertEquals(0, statisticsDetail(otherTask, true).path("overview").path("participantCount").asInt());

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

  @Test void importedShortAnswerCanBeDrawnInCopiedRandomPaper() throws Exception {
    long bank = request(post("/management/banks").header("Authorization", authorization),
        Map.of("name", "问答题回归", "category", "安全", "published", true)).path("id").asLong();
    String reference = "  Stop, then report.\n记录时间、地点。 ";
    byte[] bytes;
    try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var output = new java.io.ByteArrayOutputStream()) {
      var sheet = book.createSheet();
      sheet.createRow(0).createCell(0).setCellValue("题型");
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("问答题");
      row.createCell(1).setCellValue("如何处理事故？");
      row.createCell(6).setCellValue(reference);
      row.createCell(7).setCellValue("解析包含评分要点");
      book.write(output);
      bytes = output.toByteArray();
    }
    var file = new org.springframework.mock.web.MockMultipartFile("file", "short.xlsx", null, bytes);
    mvc.perform(multipart("/management/questions/import").file(file).param("bankId", Long.toString(bank))
        .header("Authorization", authorization)).andExpect(status().isOk());
    assertEquals(reference, jdbc.queryForObject("SELECT answer_text FROM question_item WHERE bank_id=?", String.class, bank));
    var publicQuestion = request(get("/public/banks/" + bank + "/questions"), Map.of()).path("records").get(0);
    assertEquals("SHORT_ANSWER", publicQuestion.path("questionType").asText());
    assertNull(publicQuestion.path("answerText").textValue());
    assertNull(publicQuestion.path("analysis").textValue());
    assertEquals(0, publicQuestion.path("options").size());
    long paper = request(post("/management/papers").header("Authorization", authorization),
        Map.of("name", "随机问答卷", "mode", "RANDOM", "totalScore", 10, "passScore", 6)).path("id").asLong();
    String paperUrl = "/management/papers/" + paper;
    request(post(paperUrl + "/random-config/banks").header("Authorization", authorization), Map.of("bankId", bank));
    var config = request(get(paperUrl + "/random-config").header("Authorization", authorization), Map.of()).get(0);
    assertEquals(1, config.path("availableShortAnswerCount").asInt());
    var draw = Map.of("bankId", bank, "shortAnswerCount", 1, "shortAnswerScore", 10);
    request(put(paperUrl + "/random-config").header("Authorization", authorization), List.of(draw));
    var published = request(patch(paperUrl + "/status").param("value", "PUBLISHED").header("Authorization", authorization), Map.of());
    assertEquals(1, published.path("questionCount").asInt());
    var copy = request(post(paperUrl + "/copy").header("Authorization", authorization), Map.of("name", "副本"));
    var copied = request(get("/management/papers/" + copy.path("id").asLong() + "/random-config")
        .header("Authorization", authorization), Map.of()).get(0);
    assertEquals(1, copied.path("shortAnswerCount").asInt());
    assertEquals(10, copied.path("shortAnswerScore").asInt());
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
    var before = jdbc.queryForList("SELECT employee_id FROM training_audience WHERE training_id=? ORDER BY employee_id", Long.class, first);
    mvc.perform(put("/management/trainings/" + first).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit))).andExpect(status().isBadRequest());
    assertEquals(before, jdbc.queryForList("SELECT employee_id FROM training_audience WHERE training_id=? ORDER BY employee_id", Long.class, first));
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
    long secondEmployee = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'第二人',?)", owner, department, "137" + String.format("%08d", SEQUENCE.incrementAndGet()));
    long secondExam = samePaperExam(exam);
    var initial = trainingCommand(secondExam, false);
    initial.put("audience", Map.of("type", "USERS", "ids", List.of(secondEmployee)));
    long second = request(post("/management/trainings").header("Authorization", authorization), initial).path("id").asLong();
    long newcomer = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'竞争分配',?)", owner, department, "137" + String.format("%08d", SEQUENCE.incrementAndGet()));
    var body = trainingCommand(secondExam, false);
    body.put("version", trainings.get(second, owner).get("version"));
    body.put("audience", Map.of("type", "USERS", "ids", List.of(secondEmployee, newcomer)));
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

  @Test void anExamCannotBeAttachedToAnotherTrainingEvenWhenLegacyUnmanaged() throws Exception {
    long exam = createExam();
    long first = createTraining(exam, false);
    var body = trainingCommand(exam, true);
    mvc.perform(post("/management/trainings").header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    long second = createTraining(null, true);
    body.put("version", trainings.get(second, owner).get("version"));
    mvc.perform(put("/management/trainings/" + second).header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    assertEquals(exam, ((Number)trainings.get(first, owner).get("assessmentId")).longValue());
    assertNull(trainings.get(second, owner).get("assessmentId"));
  }

  @Test void databaseRejectsSharedExamButAllowsIndependentExamsUsingSamePaper() throws Exception {
    long exam = createExam();
    long first = createTraining(exam, false);
    long secondExam = samePaperExam(exam);
    jdbc.update("UPDATE assessment_task SET training_managed=TRUE WHERE id IN (?,?)", exam, secondExam);
    long second = createTraining(secondExam, true);
    assertThrows(org.springframework.dao.DuplicateKeyException.class,
        () -> jdbc.update("UPDATE training SET assessment_id=? WHERE id=?", exam, second));
    assertDoesNotThrow(() -> learning.learning(exam, "测试学员", phone));
    jdbc.update("UPDATE training SET end_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 DAY) WHERE id=?", second);
    assertDoesNotThrow(() -> learning.learning(exam, "测试学员", phone));
    assertEquals(exam, ((Number)trainings.get(first, owner).get("assessmentId")).longValue());
  }

  @Test void reviewVideosUseConfiguredOrderAndDoNotExposeStoragePaths() throws Exception {
    long training = createTraining(null, false);
    insert("INSERT INTO training_video(training_id,title,storage_path,content_type,sort_order) VALUES(?,'later','private1','video/mp4',2)", training);
    insert("INSERT INTO training_video(training_id,title,storage_path,content_type,sort_order) VALUES(?,'first','private2','video/mp4',1)", training);
    var result = json.readTree(mvc.perform(get("/learner/trainings/" + training + "/review")
        .header("Authorization", learnerToken(training))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString()).path("data");
    assertEquals("first", result.path("videos").get(0).path("title").asText());
    assertEquals("later", result.path("videos").get(1).path("title").asText());
    assertFalse(result.toString().contains("private"));
  }

  @Test void copyFormOnlyCreatesOnValidFinalSubmission() throws Exception {
    long source = createTraining(null, false);
    material(source);
    when(files.copyMaterial(anyMap())).thenReturn("eds:training-materials/form-copy.pdf");
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    mvc.perform(get("/management/trainings/" + source).header("Authorization", authorization)).andExpect(status().isOk());
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
    var command = trainingCommand(null, true);
    command.put("name", "表单修改后的副本");
    command.put("audience", Map.of("type", "USERS", "ids", List.of(employee)));
    var saved = request(post("/management/trainings/" + source + "/copy").header("Authorization", authorization), Map.of("sourceVersion", trainings.get(source, owner).get("version"), "training", command));
    assertEquals("表单修改后的副本", saved.path("name").asText());
    assertEquals(before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
    assertEquals(List.of(employee), jdbc.queryForList("SELECT employee_id FROM training_audience WHERE training_id=?", Long.class, saved.path("id").asLong()));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_material WHERE training_id=?", Integer.class, saved.path("id").asLong()));
    assertNotEquals("表单修改后的副本", trainings.get(source, owner).get("name"));
    command.put("name", "");
    mvc.perform(post("/management/trainings/" + source + "/copy").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceVersion", trainings.get(source, owner).get("version"), "training", command)))).andExpect(status().isBadRequest());
    assertEquals(before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
  }

  @Test void copyDraftEditsMaterialsBeforeCreatingAndKeepsSourceUnchanged() throws Exception {
    long source = createTraining(null, false);
    long first = material(source), removed = material(source), last = material(source);
    when(files.copyMaterial(anyMap())).thenAnswer(call -> "eds:copy/" + ((Map<?, ?>) call.getArgument(0)).get("id"));
    when(storage.uploadMaterial(any(), anyString(), anyString(), anyString())).thenReturn("eds:copy/new.png");
    var imageBytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", imageBytes);
    var upload = new org.springframework.mock.web.MockMultipartFile("files", "new.png", "image/png", imageBytes.toByteArray());
    var times = List.of(Map.of("pageNumber", 1, "requiredSeconds", 45), Map.of("pageNumber", 2, "requiredSeconds", 90));
    var items = List.of(Map.of("kind", "materials", "sourceId", last, "pages", times),
      Map.of("kind", "materials", "uploadIndex", 0, "pages", List.of(Map.of("pageNumber", 1, "requiredSeconds", 30))),
      Map.of("kind", "materials", "sourceId", first, "pages", times));
    var copy = Map.of("sourceVersion", trainings.get(source, owner).get("version"), "training", trainingCommand(null, false));
    var draft = new org.springframework.mock.web.MockMultipartFile("draft", "draft.json", "application/json", json.writeValueAsBytes(Map.of("copy", copy, "items", items)));
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    var response = mvc.perform(multipart("/management/trainings/" + source + "/copy-with-materials").file(draft).file(upload).header("Authorization", authorization)).andExpect(status().isOk()).andReturn();
    long created = json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
    assertTrue(created > 0);
    assertEquals(before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
    assertEquals(List.of("eds:copy/" + last, "eds:copy/new.png", "eds:copy/" + first), jdbc.queryForList("SELECT storage_key FROM training_material WHERE training_id=? ORDER BY sort_order,id", String.class, created));
    assertEquals(List.of(45,90,30,45,90), jdbc.queryForList("SELECT p.required_seconds FROM training_material m JOIN training_material_page p ON p.material_id=m.id WHERE m.training_id=? ORDER BY m.sort_order,p.page_number", Integer.class, created));
    assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM training_material WHERE training_id=?", Integer.class, source));
    assertEquals(List.of(5,5), jdbc.queryForList("SELECT required_seconds FROM training_material_page WHERE material_id=? ORDER BY page_number", Integer.class, removed));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM candidate_learning_session WHERE training_id=?", Integer.class, created));
  }

  @Test void copyDraftMixesExistingAndUploadedVideosInSelectedOrder() throws Exception {
    long source = createTraining(null, false);
    trainings.addVideo(source, owner, "original.mp4", "old.mp4", "video/mp4");
    long video = jdbc.queryForObject("SELECT id FROM training_video WHERE training_id=?", Long.class, source);
    when(files.copyVideo(anyMap())).thenReturn("eds:training-videos/copied.mp4");
    when(storage.uploadMaterial(any(), anyString(), anyString(), anyString())).thenReturn("eds:training-videos/uploaded.mp4");
    var upload = new org.springframework.mock.web.MockMultipartFile("files", "new.mp4", "video/mp4", new byte[]{0,0,0,12,'f','t','y','p',0,0,0,0});
    var request = new TrainingCopyService.CopyRequest(((Number) trainings.get(source, owner).get("version")).intValue(), json.convertValue(trainingCommand(null, false), TrainingService.SaveTraining.class));
    var result = copies.copyDraft(source, owner, request, List.of(
      new TrainingCopyService.MaterialSelection("videos", null, 0, null),
      new TrainingCopyService.MaterialSelection("videos", video, null, null)), List.of(upload), authorization);
    assertEquals(List.of("new.mp4", "original.mp4"), jdbc.queryForList("SELECT title FROM training_video WHERE training_id=? ORDER BY sort_order,id", String.class, result.get("id")));
    assertEquals(List.of("old.mp4"), jdbc.queryForList("SELECT storage_path FROM training_video WHERE training_id=?", String.class, source));
  }

  @Test void copyDraftRejectsForeignMaterialAndRollsBackEarlierCopies() throws Exception {
    long source = createTraining(null, false), other = createTraining(null, false);
    long ownMaterial = material(source), foreign = material(other);
    when(files.copyMaterial(anyMap())).thenReturn("eds:copy/rollback.pdf");
    var times = List.of(new TrainingMaterialService.PageTime(1,45), new TrainingMaterialService.PageTime(2,90));
    var request = new TrainingCopyService.CopyRequest(((Number) trainings.get(source, owner).get("version")).intValue(), json.convertValue(trainingCommand(null, false), TrainingService.SaveTraining.class));
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    assertThrows(BusinessException.class, () -> copies.copyDraft(source, owner, request, List.of(
      new TrainingCopyService.MaterialSelection("materials", ownMaterial, null, times),
      new TrainingCopyService.MaterialSelection("materials", foreign, null, times)), List.of(), authorization));
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
    verify(storage).delete("eds:copy/rollback.pdf");
    var empty = copies.copyDraft(source, owner, request, List.of(), List.of(), authorization);
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM training_material WHERE training_id=?", Integer.class, empty.get("id")));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_material WHERE training_id=?", Integer.class, source));
  }

  @Test void copyFormStorageFailureRollsBackTrainingRosterAndBinding() throws Exception {
    long source = createTraining(null, false);
    material(source); material(source);
    long exam = createExam();
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    var command = json.convertValue(trainingCommand(exam, true), TrainingService.SaveTraining.class);
    var request = new TrainingCopyService.CopyRequest(((Number)trainings.get(source, owner).get("version")).intValue(), command);
    when(files.copyMaterial(anyMap())).thenReturn("eds:training-materials/form-rollback.pdf").thenThrow(new IllegalStateException("copy failed"));
    assertThrows(IllegalStateException.class, () -> copies.copyAndSave(source, owner, request));
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learner_paper_binding WHERE owner_id=?", Integer.class, owner));
    verify(storage).delete("eds:training-materials/form-rollback.pdf");
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM training_material WHERE training_id=?", Integer.class, source));
  }

  @Test void copyFormRejectsMissingConfigurationStaleSourceAndOtherOwner() throws Exception {
    long source = createTraining(null, false);
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    mvc.perform(post("/management/trainings/" + source + "/copy").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    mvc.perform(post("/management/trainings/" + source + "/copy").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceVersion", -1, "training", trainingCommand(null, true))))).andExpect(status().isBadRequest());
    var request = new TrainingCopyService.CopyRequest(((Number)trainings.get(source, owner).get("version")).intValue(), json.convertValue(trainingCommand(null, true), TrainingService.SaveTraining.class));
    assertThrows(BusinessException.class, () -> copies.copyAndSave(source, owner + 100000, request));
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
    verifyNoInteractions(files);
  }

  @Test void trainingExamIsSavedAtomicallyAndReusesPaperAcrossTrainings() throws Exception {
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    jdbc.update("UPDATE test_paper SET status='PUBLISHED' WHERE id=?", paper);
    var body = trainingCommand(null, false);
    body.put("exam", examSettings(paper));
    var first = request(post("/management/trainings").header("Authorization", authorization), body);
    assertTrue(first.path("assessmentId").asLong() > 0, "Final save must create a training-specific exam");
    assertEquals(paper, first.path("exam").path("paperId").asLong());
    var second = request(post("/management/trainings").header("Authorization", authorization), body);
    assertNotEquals(first.path("assessmentId").asLong(), second.path("assessmentId").asLong());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_paper WHERE id=?", Integer.class, paper));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE training_id=? AND employee_id=?", Integer.class, second.path("id").asLong(), employee));
    long tasks = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner);
    body.put("audience", Map.of("type", "USERS", "ids", List.of()));
    mvc.perform(post("/management/trainings").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
    assertEquals(tasks, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner));
    body.put("audience", Map.of("type", "ALL", "ids", List.of()));
    body.put("version", first.path("version").asInt());
    var updated = request(put("/management/trainings/" + first.path("id").asLong()).header("Authorization", authorization), body);
    assertEquals(first.path("assessmentId").asLong(), updated.path("assessmentId").asLong());
    assertEquals(tasks, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner));
  }

  @Test void trainingExamRejectsInvalidSettingsForeignPapersAndStaleUpdates() throws Exception {
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    jdbc.update("UPDATE test_paper SET status='PUBLISHED' WHERE id=?", paper);
    var body = trainingCommand(null, false);
    var settings = new HashMap<>(examSettings(paper));
    settings.put("durationMinutes", 0); body.put("exam", settings);
    mvc.perform(post("/management/trainings").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
    settings.put("durationMinutes", 30); settings.put("paperId", 999999999L);
    mvc.perform(post("/management/trainings").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isNotFound());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Integer.class, owner));
    settings.put("paperId", paper);
    var saved = request(post("/management/trainings").header("Authorization", authorization), body);
    body.put("version", 0); settings.put("durationMinutes", 45);
    mvc.perform(put("/management/trainings/" + saved.path("id").asLong()).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
    assertEquals(30, jdbc.queryForObject("SELECT duration_minutes FROM assessment_task WHERE id=?", Integer.class, saved.path("assessmentId").asLong()));
    var steal = trainingCommand(saved.path("assessmentId").asLong(), false);
    mvc.perform(post("/management/trainings").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(steal))).andExpect(status().isBadRequest());
  }

  @Test void copyingTrainingCreatesItsOwnExamOnlyOnFinalSaveAndRollsBackOnFileFailure() throws Exception {
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    jdbc.update("UPDATE test_paper SET status='PUBLISHED' WHERE id=?", paper);
    var body = trainingCommand(null, false); body.put("exam", examSettings(paper));
    var source = request(post("/management/trainings").header("Authorization", authorization), body);
    long sourceId = source.path("id").asLong();
    var copied = request(post("/management/trainings/" + sourceId + "/copy").header("Authorization", authorization), Map.of("sourceVersion", source.path("version").asInt(), "training", body));
    assertNotEquals(source.path("assessmentId").asLong(), copied.path("assessmentId").asLong());
    assertEquals(paper, copied.path("exam").path("paperId").asLong());
    material(sourceId);
    when(files.copyMaterial(anyMap())).thenThrow(new IllegalStateException("test storage failure"));
    long count = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner);
    mvc.perform(post("/management/trainings/" + sourceId + "/copy").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("sourceVersion", source.path("version").asInt(), "training", body)))).andExpect(status().is5xxServerError());
    assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner));
  }

  @Test void trainingExamLocksRulesAfterParticipationAndRetiresUnlinkedTasks() throws Exception {
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    jdbc.update("UPDATE test_paper SET status='PUBLISHED' WHERE id=?", paper);
    var body = trainingCommand(null, false); body.put("exam", examSettings(paper));
    var saved = request(post("/management/trainings").header("Authorization", authorization), body);
    long training = saved.path("id").asLong(), exam = saved.path("assessmentId").asLong();
    long participant = insert("INSERT INTO assessment_participant(task_id,candidate_name,phone,employee_id) VALUES(?,'测试学员',?,?)", exam, phone, employee);
    body.put("version", saved.path("version").asInt());
    var beforeExam = json.convertValue(saved.path("exam"), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
    beforeExam.put("durationMinutes", 45); body.put("exam", beforeExam);
    saved = request(put("/management/trainings/" + training).header("Authorization", authorization), body);
    assertEquals(45, saved.path("exam").path("durationMinutes").asInt());
    insert("INSERT INTO assessment_attempt(task_id,participant_id,attempt_no,status,started_at) VALUES(?,?,1,'IN_PROGRESS',CURRENT_TIMESTAMP(3))", exam, participant);
    body.put("version", saved.path("version").asInt());
    var settings = json.convertValue(saved.path("exam"), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
    settings.put("durationMinutes", 60); body.put("exam", settings);
    mvc.perform(put("/management/trainings/" + training).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
    settings.put("durationMinutes", 45); body.put("endAt", LocalDateTime.now().plusDays(4).toString());
    request(put("/management/trainings/" + training).header("Authorization", authorization), body);
    var anotherBody = trainingCommand(null, false); anotherBody.put("exam", examSettings(paper));
    var another = request(post("/management/trainings").header("Authorization", authorization), anotherBody);
    anotherBody.put("version", another.path("version").asInt()); anotherBody.put("exam", Map.of("enabled", false));
    var unlinked = request(put("/management/trainings/" + another.path("id").asLong()).header("Authorization", authorization), anotherBody);
    assertTrue(unlinked.path("assessmentId").isNull());
    assertEquals("DISABLED", jdbc.queryForObject("SELECT status FROM assessment_task WHERE id=?", String.class, another.path("assessmentId").asLong()));
  }

  @Test void questionKeywordSearchSupportsBankFilteringAndEmptyResults() throws Exception {
    long bank = insert("INSERT INTO question_bank(name,category,created_by,created_at,updated_at) VALUES('搜索回归','安全',?,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", owner);
    insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,score,created_by) VALUES(?,'SINGLE','选择 A','A',100,?)", bank, owner);
    var query = new com.aiquestion.common.PageQuery();
    query.setKeyword("选择");
    assertEquals(1, questionItems.count(bank, query));
    assertEquals(1, questionItems.findPage(bank, query).size());
    query.setPageNo(2);
    assertTrue(questionItems.findPage(bank, query).isEmpty());
    query.setKeyword("不存在的题目");
    assertEquals(0, questionItems.count(bank, query));
    assertTrue(questionItems.findPage(bank, query).isEmpty());
  }

  @Test void endingTrainingExamOnlyStopsItsOwnTask() throws Exception {
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    jdbc.update("UPDATE test_paper SET status='PUBLISHED' WHERE id=?", paper);
    var body = trainingCommand(null, false); body.put("exam", examSettings(paper));
    var first = request(post("/management/trainings").header("Authorization", authorization), body);
    var second = request(post("/management/trainings").header("Authorization", authorization), body);
    long firstTask = first.path("assessmentId").asLong();
    long participant = insert("INSERT INTO assessment_participant(task_id,candidate_name,phone,employee_id) VALUES(?,'测试学员',?,?)", firstTask, phone, employee);
    long activeAttempt = insert("INSERT INTO assessment_attempt(task_id,participant_id,attempt_no,status,started_at) VALUES(?,?,1,'IN_PROGRESS',CURRENT_TIMESTAMP(3))", firstTask, participant);
    var ended = request(post("/management/trainings/" + first.path("id").asLong() + "/end").header("Authorization", authorization), Map.of());
    assertTrue(jdbc.queryForObject("SELECT end_at <= CURRENT_TIMESTAMP(3) FROM training WHERE id=?", Boolean.class, first.path("id").asLong()));
    assertEquals(jdbc.queryForObject("SELECT end_at FROM training WHERE id=?", java.sql.Timestamp.class, first.path("id").asLong()),
        jdbc.queryForObject("SELECT end_at FROM assessment_task WHERE id=?", java.sql.Timestamp.class, firstTask));
    body.put("version", ended.path("version").asInt());
    body.put("startAt", ended.path("startAt").asText());
    body.put("endAt", ended.path("endAt").asText());
    request(put("/management/trainings/" + first.path("id").asLong()).header("Authorization", authorization), body);
    // Historical force-ended trainings may still have the original future exam deadline.
    jdbc.update("UPDATE assessment_task SET end_at=DATE_ADD(end_at,INTERVAL 1 DAY) WHERE id=?", firstTask);
    body.put("version", jdbc.queryForObject("SELECT version FROM training WHERE id=?", Integer.class, first.path("id").asLong()));
    request(put("/management/trainings/" + first.path("id").asLong()).header("Authorization", authorization), body);
    assertEquals("DISABLED", jdbc.queryForObject("SELECT status FROM assessment_task WHERE id=?", String.class, firstTask));
    assertEquals(jdbc.queryForObject("SELECT end_at FROM training WHERE id=?", java.sql.Timestamp.class, first.path("id").asLong()),
        jdbc.queryForObject("SELECT end_at FROM assessment_task WHERE id=?", java.sql.Timestamp.class, firstTask));
    long pure = createTraining(null, false);
    request(post("/management/trainings/" + pure + "/end").header("Authorization", authorization), Map.of());
    assertTrue(jdbc.queryForObject("SELECT end_at <= CURRENT_TIMESTAMP(3) FROM training WHERE id=?", Boolean.class, pure));
    assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM assessment_attempt WHERE id=?", String.class, activeAttempt));
    assertNotNull(jdbc.queryForObject("SELECT submitted_at FROM assessment_attempt WHERE id=?", java.sql.Timestamp.class, activeAttempt));
    assertEquals("DISABLED", jdbc.queryForObject("SELECT status FROM assessment_task WHERE id=?", String.class, first.path("assessmentId").asLong()));
    assertNotEquals("DISABLED", jdbc.queryForObject("SELECT status FROM assessment_task WHERE id=?", String.class, second.path("assessmentId").asLong()));
    var list = json.readTree(mvc.perform(get("/management/trainings").header("Authorization", authorization)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertTrue(java.util.stream.StreamSupport.stream(list.spliterator(), false).anyMatch(t -> t.path("id").asLong()==first.path("id").asLong() && "DISABLED".equals(t.path("examStatus").asText())));

  }

  @Test void unpublishedPaperCannotBeUsedForNewTrainingExam() throws Exception {
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    var body = trainingCommand(null, false); body.put("exam", examSettings(paper));
    mvc.perform(post("/management/trainings").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Integer.class, owner));
  }

  @Test void createTrainingWithFilesIsAtomic() throws Exception {
    when(storage.uploadMaterial(any(), anyString(), anyString(), anyString())).thenReturn("eds:training-videos/11111111-1111-1111-1111-111111111111.mp4");
    var command = new org.springframework.mock.web.MockMultipartFile("training", "training.json", "application/json", json.writeValueAsBytes(trainingCommand(null, false)));
    var video = new org.springframework.mock.web.MockMultipartFile("videos", "intro.mp4", "video/mp4", new byte[]{0,0,0,12,'f','t','y','p',0,0,0,0});
    var imageBytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", imageBytes);
    var image = new org.springframework.mock.web.MockMultipartFile("materials", "guide.png", "image/png", imageBytes.toByteArray());
    var response = mvc.perform(multipart("/management/trainings/with-materials").file(command).file(video).file(image).header("Authorization", authorization)).andExpect(status().isOk()).andReturn();
    long id = json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_video WHERE training_id=?", Integer.class, id));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_material WHERE training_id=?", Integer.class, id));
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    var invalid = new org.springframework.mock.web.MockMultipartFile("materials", "bad.pdf", "application/pdf", new byte[]{1,2,3});
    mvc.perform(multipart("/management/trainings/with-materials").file(command).file(video).file(invalid).header("Authorization", authorization)).andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
    verify(storage).delete("eds:training-videos/11111111-1111-1111-1111-111111111111.mp4");
  }

  @Test void draftPdfInspectionAndPageTimingsPersistOnCreate() throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
      pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage()); pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage()); pdf.save(bytes);
    }
    var inspection = new org.springframework.mock.web.MockMultipartFile("file", "guide.pdf", "application/pdf", bytes.toByteArray());
    var checked = mvc.perform(multipart("/management/trainings/materials/inspect").file(inspection).header("Authorization", authorization)).andExpect(status().isOk()).andReturn();
    assertEquals(2, json.readTree(checked.getResponse().getContentAsString()).path("data").path("pageCount").asInt());
    verifyNoInteractions(storage);
    when(storage.uploadMaterial(any(), anyString(), anyString(), anyString())).thenReturn("eds:training-materials/test.pdf");
    var file = new org.springframework.mock.web.MockMultipartFile("materials", "guide.pdf", "application/pdf", bytes.toByteArray());
    var command = new org.springframework.mock.web.MockMultipartFile("training", "training.json", "application/json", json.writeValueAsBytes(trainingCommand(null, false)));
    var timings = new org.springframework.mock.web.MockMultipartFile("materialTimings", "times.json", "application/json", "[[{\"pageNumber\":1,\"requiredSeconds\":45},{\"pageNumber\":2,\"requiredSeconds\":90}]]".getBytes());
    var result = mvc.perform(multipart("/management/trainings/with-materials").file(command).file(file).file(timings).header("Authorization", authorization)).andExpect(status().isOk()).andReturn();
    long id = json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    assertEquals(List.of(45,90), jdbc.queryForList("SELECT p.required_seconds FROM training_material_page p JOIN training_material m ON m.id=p.material_id WHERE m.training_id=? ORDER BY p.page_number", Integer.class, id));
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner);
    var invalid = new org.springframework.mock.web.MockMultipartFile("materialTimings", "times.json", "application/json", "[[{\"pageNumber\":1,\"requiredSeconds\":0}]]".getBytes());
    mvc.perform(multipart("/management/trainings/with-materials").file(command).file(file).file(invalid).header("Authorization", authorization)).andExpect(status().isBadRequest());
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner));
  }

  @Test void trainingStatisticsSumOnlyOwnMaterialsAndDoNotDuplicateReentry() throws Exception {
    long first = createTraining(null, false), second = createTraining(null, false);
    long material = material(first), otherMaterial = material(second);
    long video = insert("INSERT INTO training_video(training_id,title,storage_path,content_type) VALUES(?,'统计视频','stats.mp4','video/mp4')", first);
    var entry = learning.learning(-first, "测试学员", phone);
    learning.learning(-first, "测试学员", phone);
    long session = jdbc.queryForObject("SELECT id FROM candidate_learning_session WHERE token=?", Long.class, entry.get("token"));
    jdbc.update("UPDATE candidate_video_progress SET studied_seconds=45,completed=TRUE WHERE session_id=? AND video_id=?", session, video);
    jdbc.update("INSERT INTO candidate_material_progress(session_id,material_id,page_number,studied_seconds,completed) VALUES(?,?,1,60,TRUE),(?,?,2,120,FALSE)", session, material, session, material);
    var otherEntry = learning.learning(-second, "测试学员", phone);
    long otherSession = jdbc.queryForObject("SELECT id FROM candidate_learning_session WHERE token=?", Long.class, otherEntry.get("token"));
    jdbc.update("INSERT INTO candidate_material_progress(session_id,material_id,page_number,studied_seconds,completed) VALUES(?,?,1,999,TRUE)", otherSession, otherMaterial);
    var rows = trainingLogs.list(first, owner);
    assertEquals(1, rows.size());
    assertEquals(225.0, ((Number) rows.get(0).get("studiedSeconds")).doubleValue(), 0.001);
    assertEquals(false, rows.get(0).get("completed"));
    assertFalse(trainingLogs.completedParticipants(first, owner).contains(phone + "|测试学员"));
    jdbc.update("UPDATE candidate_material_progress SET completed=TRUE WHERE session_id=?", session);
    assertEquals(true, trainingLogs.list(first, owner).get(0).get("completed"));
    assertTrue(trainingLogs.completedParticipants(first, owner).contains(phone + "|测试学员"));
    jdbc.update("UPDATE candidate_video_progress SET studied_seconds=NULL WHERE session_id=?", session);
    assertEquals(180.0, ((Number) trainingLogs.list(first, owner).get(0).get("studiedSeconds")).doubleValue(), 0.001);
    long empty = createTraining(null, false);
    assertTrue(trainingLogs.list(empty, owner).isEmpty());
    learning.learning(-empty, "测试学员", phone);
    assertEquals(false, trainingLogs.list(empty, owner).get(0).get("completed"));
    learning.requireComplete(-empty, "测试学员", phone);
    assertEquals(true, trainingLogs.list(empty, owner).get(0).get("completed"));
  }

  @Test void trainingStatisticsKeepSamePaperExamTrainingsIndependent() throws Exception {
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    jdbc.update("UPDATE test_paper SET status='PUBLISHED' WHERE id=?", paper);
    var body = trainingCommand(null, false); body.put("exam", examSettings(paper));
    var first = request(post("/management/trainings").header("Authorization", authorization), body);
    var second = request(post("/management/trainings").header("Authorization", authorization), body);
    long training = first.path("id").asLong(), other = second.path("id").asLong();
    long material = material(training); material(other);
    var entry = learning.learning(first.path("assessmentId").asLong(), "测试学员", phone);
    long session = jdbc.queryForObject("SELECT id FROM candidate_learning_session WHERE token=?", Long.class, entry.get("token"));
    jdbc.update("INSERT INTO candidate_material_progress(session_id,material_id,page_number,studied_seconds,completed) VALUES(?,?,1,30,TRUE)", session, material);
    assertEquals(30.0, ((Number) trainingLogs.list(training, owner).get(0).get("studiedSeconds")).doubleValue(), 0.001);
    assertTrue(trainingLogs.list(other, owner).isEmpty());
    learning.learning(second.path("assessmentId").asLong(), "测试学员", phone);
    assertEquals(0.0, ((Number) trainingLogs.list(other, owner).get(0).get("studiedSeconds")).doubleValue(), 0.001);
    assertEquals(false, trainingLogs.list(other, owner).get(0).get("completed"));
  }

  @Test void trainingTimesAreRequiredAndOverrideExamTimes() throws Exception {
    var missing = trainingCommand(null, false); missing.remove("startAt");
    mvc.perform(post("/management/trainings").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(missing))).andExpect(status().isBadRequest());
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    jdbc.update("UPDATE test_paper SET status='PUBLISHED' WHERE id=?", paper);
    var body = trainingCommand(null, false);
    var namedExam = new HashMap<String,Object>(examSettings(paper));
    namedExam.put("name", "独立考试名称");
    body.put("exam", namedExam);
    var created = request(post("/management/trainings").header("Authorization", authorization), body);
    assertEquals("独立考试名称", created.path("exam").path("name").asText());
    body.put("name", "修改后的培训名称");
    assertEquals(created.path("startAt").asText(), created.path("exam").path("startAt").asText());
    assertEquals(created.path("endAt").asText(), created.path("exam").path("endAt").asText());
    body.put("version", created.path("version").asInt()); body.put("endAt", LocalDateTime.now().plusDays(8).toString());
    var edited = request(put("/management/trainings/" + created.path("id").asLong()).header("Authorization", authorization), body);
    assertEquals(edited.path("endAt").asText(), edited.path("exam").path("endAt").asText());
    assertEquals("独立考试名称", edited.path("exam").path("name").asText());
    assertEquals("独立考试名称", jdbc.queryForObject("SELECT name FROM assessment_task WHERE id=?", String.class, edited.path("assessmentId").asLong()));
    body.remove("endAt");
    mvc.perform(put("/management/trainings/" + created.path("id").asLong()).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  }

  @Test void trainingSwitchCombinationsPersistAndClearAfterReload() throws Exception {
    long original = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, original);
    jdbc.update("UPDATE test_paper SET status='PUBLISHED' WHERE id=?", paper);
    var question = Map.of("id", "toggle-question", "type", "TEXT", "title", "评价", "required", false, "options", List.of());
    for (boolean examEnabled : List.of(false, true)) {
      for (boolean surveyEnabled : List.of(false, true)) {
        var body = trainingCommand(null, true);
        var exam = new HashMap<String, Object>(examSettings(paper));
        exam.put("enabled", examEnabled);
        body.put("exam", exam);
        body.put("surveyEnabled", surveyEnabled);
        body.put("questions", List.of(question)); // Disabled requests may contain stale drafts.
        var created = request(post("/management/trainings").header("Authorization", authorization), body);
        long id = created.path("id").asLong();
        var reloaded = json.readTree(mvc.perform(get("/management/trainings/" + id).header("Authorization", authorization))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals(examEnabled, reloaded.path("exam").path("enabled").asBoolean());
        assertEquals(surveyEnabled, reloaded.path("surveyEnabled").asBoolean());
        assertEquals(surveyEnabled ? 1 : 0, reloaded.path("questions").size());
        exam.put("enabled", false);
        body.put("surveyEnabled", false);
        body.put("version", reloaded.path("version").asInt());
        request(put("/management/trainings/" + id).header("Authorization", authorization), body);
        var cleared = json.readTree(mvc.perform(get("/management/trainings/" + id).header("Authorization", authorization))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertFalse(cleared.path("exam").path("enabled").asBoolean());
        assertTrue(cleared.path("exam").path("paperId").isNull());
        assertFalse(cleared.path("surveyEnabled").asBoolean());
        assertEquals(0, cleared.path("questions").size());
        assertEquals("[]", jdbc.queryForObject("SELECT questions_json FROM training WHERE id=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT assessment_id FROM training WHERE id=?", Long.class, id));
        exam.put("enabled", true);
        body.put("surveyEnabled", true);
        body.put("version", cleared.path("version").asInt());
        var reopened = request(put("/management/trainings/" + id).header("Authorization", authorization), body);
        assertTrue(reopened.path("exam").path("enabled").asBoolean());
        assertTrue(reopened.path("surveyEnabled").asBoolean());
        assertEquals(1, reopened.path("questions").size());
      }
    }
  }

  private Map<String, Object> examSettings(long paper) {
    return Map.of("enabled", true, "paperId", paper, "startAt", LocalDateTime.now().plusDays(1).toString(),
        "endAt", LocalDateTime.now().plusDays(2).toString(), "durationMinutes", 30,
        "allowRetake", true, "maxAttempts", 2, "shuffleQuestions", true, "shuffleOptions", true, "showResultImmediately", true);
  }

  @Test void aggregatedTimesKeepAbsoluteInstantsAndTrainingDeadlineBlocksLearning() throws Exception {
    long exam = createExam(), course = createTraining(exam, false);
    var task = publicExams.get(exam, false, null).data();
    var expectedStart = jdbc.queryForObject("SELECT start_at FROM training WHERE id=?", java.sql.Timestamp.class, course);
    assertEquals(expectedStart.toInstant(), ((java.sql.Timestamp) task.get("trainingStartAt")).toInstant());
    long doc = material(course);
    long video = insert("INSERT INTO training_video(training_id,title,storage_path,content_type) VALUES(?,'时区视频','time.mp4','video/mp4')", course);
    var entry = learning.learning(exam, "测试学员", phone);
    long session = jdbc.queryForObject("SELECT id FROM candidate_learning_session WHERE token=?", Long.class, entry.get("token"));
    var completed = java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T09:02:21Z"));
    jdbc.update("UPDATE candidate_video_progress SET completed=TRUE,completed_at=? WHERE session_id=? AND video_id=?", completed, session, video);
    jdbc.update("INSERT INTO candidate_material_progress(session_id,material_id,page_number,studied_seconds,completed,completed_at) VALUES(?,?,1,5,TRUE,?),(?,?,2,5,TRUE,?)", session, doc, completed, session, doc, completed);
    var identity = new AccountIdentity(employee, AccountType.EMPLOYEE, owner);
    var row = learnerRecords.trainings(identity).stream().filter(r -> ((Number)r.get("id")).longValue()==course).findFirst().orElseThrow();
    assertEquals(completed.toInstant(), ((java.sql.Timestamp)row.get("completedAt")).toInstant());
    var detail = learnerRecords.detail(identity, course);
    for (var part : (List<Map<String,Object>>)detail.get("materials"))
      assertEquals(completed.toInstant(), ((java.sql.Timestamp)part.get("completedAt")).toInstant());
    jdbc.update("UPDATE training SET end_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE id=?", course);
    assertThrows(BusinessException.class, () -> learning.learning(exam, "测试学员", phone));
    assertThrows(BusinessException.class, () -> learning.progress(exam, video, (String)entry.get("token"), 0, 20, false));
    assertThrows(BusinessException.class, () -> reading.session(exam, (String)entry.get("token")));
    assertDoesNotThrow(() -> learnerRecords.detail(identity, course));
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

  @Test void editingAudienceOnlyAppendsAndPreservesExistingLearningAndRosterSnapshots() throws Exception {
    for (Long exam : Arrays.asList(null, createExam())) {
      var body = trainingCommand(exam, false);
      body.put("audience", Map.of("type", "USERS", "ids", List.of(employee)));
      long training = request(post("/management/trainings").header("Authorization", authorization), body).path("id").asLong();
      var entry = learning.learning(exam == null ? -training : exam, "测试学员", phone);
      var original = jdbc.queryForMap("SELECT * FROM training_audience WHERE training_id=? AND employee_id=?", training, employee);
      String extraPhone = "136" + String.format("%08d", SEQUENCE.incrementAndGet());
      long extra = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'追加学员',?)", owner, department, extraPhone);
      body.put("version", trainings.get(training, owner).get("version"));
      body.put("audience", Map.of("type", "USERS", "ids", List.of(employee, extra)));
      request(put("/management/trainings/" + training).header("Authorization", authorization), body);
      assertEquals(original, jdbc.queryForMap("SELECT * FROM training_audience WHERE training_id=? AND employee_id=?", training, employee));
      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE training_id=?", Integer.class, training));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM candidate_learning_session WHERE token=?", Integer.class, entry.get("token")));
      assertNotNull(learning.learning(exam == null ? -training : exam, "追加学员", extraPhone).get("token"));
      body.put("version", trainings.get(training, owner).get("version"));
      body.put("audience", Map.of("type", "USERS", "ids", List.of(extra)));
      mvc.perform(put("/management/trainings/" + training).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM training_audience WHERE training_id=?", Integer.class, training));
      // A stale browser cannot reopen an ended training and append in one save.
      jdbc.update("UPDATE training SET end_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE id=?", training);
      body.put("audience", Map.of("type", "ALL", "ids", List.of()));
      mvc.perform(put("/management/trainings/" + training).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
    }
  }

  @Test void appendingDepartmentKeepsInactiveOriginalAssignments() throws Exception {
    var body = trainingCommand(null, false);
    body.put("audience", Map.of("type", "DEPARTMENT", "ids", List.of(department)));
    long training = request(post("/management/trainings").header("Authorization", authorization), body).path("id").asLong();
    var original = jdbc.queryForMap("SELECT * FROM training_audience WHERE training_id=? AND employee_id=?", training, employee);
    jdbc.update("UPDATE organization_employee SET status='INACTIVE' WHERE id=?", employee);
    long extraDepartment = insert("INSERT INTO organization_department(created_by,name) VALUES(?,'追加部门')", owner);
    long extra = insert("INSERT INTO organization_employee(created_by,department_id,name,phone) VALUES(?,?,'追加人员',?)", owner, extraDepartment, "135" + String.format("%08d", SEQUENCE.incrementAndGet()));
    body.put("version", trainings.get(training, owner).get("version"));
    body.put("audience", Map.of("type", "DEPARTMENT", "ids", List.of(department, extraDepartment)));
    request(put("/management/trainings/" + training).header("Authorization", authorization), body);
    assertEquals(original, jdbc.queryForMap("SELECT * FROM training_audience WHERE training_id=? AND employee_id=?", training, employee));
    assertEquals(List.of(employee, extra), jdbc.queryForList("SELECT employee_id FROM training_audience WHERE training_id=? ORDER BY employee_id", Long.class, training));
    body.put("version", trainings.get(training, owner).get("version"));
    body.put("audience", Map.of("type", "DEPARTMENT", "ids", List.of(extraDepartment)));
    mvc.perform(put("/management/trainings/" + training).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
  }

  private JsonNode statisticsDetail(long task, boolean includeRetake) throws Exception {
    return json.readTree(mvc.perform(get("/management/assessments/" + task + "/statistics/detail").param("includeRetake", Boolean.toString(includeRetake)).header("Authorization", authorization)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
  }

  @Test void mixedExamRequiresOwnerReviewBeforeScoresOrRetakes() throws Exception {
    long exam = createExam();
    long paper = jdbc.queryForObject("SELECT paper_id FROM assessment_task WHERE id=?", Long.class, exam);
    jdbc.update("UPDATE assessment_task SET allow_retake=TRUE,max_attempts=3,show_result_immediately=TRUE WHERE id=?", exam);
    jdbc.update("UPDATE test_paper SET total_score=100,pass_score=60 WHERE id=?", paper);
    long bank = insert("INSERT INTO question_bank(name,category,created_by,created_at,updated_at) VALUES('阅卷验收','培训',?,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))", owner);
    long choice = insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,created_by) VALUES(?,'SINGLE','选择','A',?)", bank,owner);
    long essay = insert("INSERT INTO question_item(bank_id,question_type,stem,answer_text,created_by) VALUES(?,'SHORT_ANSWER','原题','Alpha beta',?)", bank,owner);
    jdbc.update("INSERT INTO test_paper_question(paper_id,question_id,score) VALUES(?,?,45),(?,?,55)",paper,choice,paper,essay);
    var candidate = Map.of("name","测试学员","phone",phone);
    String token = "Bearer " + request(post("/learner/session"),Map.of("type","EXAM","id",exam,"name","测试学员","phone",phone)).path("token").path("accessToken").asText();
    String start = "/public/exams/" + exam + "/start";
    long attempt = request(post(start).header("Authorization",token),candidate).path("attemptId").asLong();
    String path = "/public/exams/attempts/" + attempt;
    String queue = "/management/assessments/" + exam + "/grading";
    jdbc.update("UPDATE question_item SET stem='修改后',answer_text='修改后参考答案' WHERE id=?",essay);
    request(put(path+"/answers/"+essay).header("Authorization",token), Map.of("answer","My Answer, a B!"));
    request(put(path+"/answers/"+choice).header("Authorization",token), Map.of("answer","A"));
    JsonNode pending = request(post(path+"/submit").header("Authorization",token),Map.of());
    assertTrue(pending.path("pendingReview").asBoolean());
    assertFalse(pending.has("score"));
    assertEquals(attempt,request(post(start).header("Authorization",token),candidate).path("attemptId").asLong());
    var queued = json.readTree(mvc.perform(get(queue).header("Authorization",authorization)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertEquals(attempt,queued.get(0).path("attemptId").asLong());
    mvc.perform(get(queue+"/"+attempt).header("Authorization",token)).andExpect(status().isForbidden());
    var qs=json.readTree(mvc.perform(get(queue+"/"+attempt).header("Authorization",authorization)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    assertEquals("My Answer, a B!",qs.get(0).path("answer").asText());
    assertEquals("Alpha beta",qs.get(0).path("referenceAnswer").asText());
    assertEquals("原题",qs.get(0).path("stem").asText());
    mvc.perform(put(queue+"/"+attempt).header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of(essay,56)))).andExpect(status().isBadRequest());
    request(put(queue+"/"+attempt).header("Authorization",authorization),Map.of(essay,25));
    var result=publicExams.result(attempt).data();
    assertEquals(70,((Number)result.get("score")).intValue());
    assertEquals(70,statisticsDetail(exam,false).path("overview").path("averageScore").asInt());
    mvc.perform(put(queue+"/"+attempt).header("Authorization",authorization).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of(essay,0)))).andExpect(status().isBadRequest());
    long next=request(post(start).header("Authorization",token),candidate).path("attemptId").asLong();
    assertNotEquals(attempt,next);
    jdbc.update("UPDATE assessment_attempt SET started_at=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 DAY) WHERE id=?",next);
    var expired=request(post("/public/exams/attempts/"+next+"/submit").header("Authorization",token),Map.of());
    assertTrue(expired.path("pendingReview").asBoolean());
    var summary=statisticsDetail(exam,false).path("overview");
    assertEquals(1,summary.path("participantCount").asInt());
    assertTrue(summary.path("averageScore").asDouble()==0);
    request(put(queue+"/"+next).header("Authorization",authorization),Map.of(essay,0));
    assertEquals(0,((Number)publicExams.result(next).data().get("score")).intValue());
  }

  private long createExam() {
    long paper = insert("INSERT INTO test_paper(name,created_by) VALUES('映射试卷',?)", owner);
    return insert("INSERT INTO assessment_task(name,paper_id,start_at,end_at,status,created_by) VALUES('映射考试',?,DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 DAY),DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 7 DAY),'STARTED',?)", paper, owner);
  }

  @Test void previewsRenderOnceAndReadStoredPagesWithOwnershipChecks() throws Exception {
    long training=createTraining(null,false), id=material(training);
    jdbc.update("UPDATE training_material SET preview_status='PENDING' WHERE id=?",id);
    var objects=new java.util.concurrent.ConcurrentHashMap<String,byte[]>();
    byte[] original;
    try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()) {
      pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage()); pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage()); pdf.save(out);original=out.toByteArray();
    }
    objects.put("eds:training-materials/original.pdf",original);
    when(storage.open(anyString())).thenAnswer(a -> new com.aiquestion.storage.ObjectStorage.StoredObject(new java.io.ByteArrayInputStream(objects.get(a.getArgument(0))),objects.get(a.getArgument(0)).length,"image/png"));
    when(storage.reservePreview(anyString())).thenAnswer(a -> "eds:training-previews/"+UUID.randomUUID()+".png");
    doAnswer(a -> { objects.put(a.getArgument(0),a.getArgument(1));return null; }).when(storage).putPreview(anyString(),any(byte[].class));
    assertThrows(BusinessException.class,()->trainingMaterials.page(training,id,1));
    previews.process(id);
    assertEquals("READY",jdbc.queryForObject("SELECT preview_status FROM training_material WHERE id=?",String.class,id));
    byte[] first=trainingMaterials.page(training,id,1);
    assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(first)));
    assertArrayEquals(first,trainingMaterials.page(training,id,1));
    assertArrayEquals(first, (byte[])trainingMaterials.learnerPage(training,id,1,true).getBody());
    when(storage.directPreviewUrl(anyString())).thenReturn(java.util.Optional.of("https://storage.example/page"));
    assertEquals(Map.of("url","https://storage.example/page"), trainingMaterials.learnerPage(training,id,1,true).getBody());
    assertThrows(BusinessException.class, () -> trainingMaterials.learnerPage(training,id,3,true));
    previews.process(id);
    verify(storage,times(1)).open("eds:training-materials/original.pdf");
    verify(storage,times(2)).putPreview(anyString(),any(byte[].class));
    mvc.perform(post("/management/trainings/"+training+"/materials/"+id+"/preview/retry")).andExpect(status().isUnauthorized());
    assertThrows(BusinessException.class,()->trainingMaterials.retryPreview(training,id,owner+999999));
    trainingMaterials.delete(training,id,owner);
    for(String key:objects.keySet()) verify(storage).delete(key);
  }

  @Test void previewFailureResumesReservedPageAndRecoversProcessingAfterRestart() throws Exception {
    long training=createTraining(null,false), id=material(training);
    jdbc.update("UPDATE training_material SET preview_status='PROCESSING',material_type='IMAGE',page_count=1 WHERE id=?",id);
    jdbc.update("DELETE FROM training_material_page WHERE material_id=? AND page_number=2",id);
    byte[] image;
    try(var out=new java.io.ByteArrayOutputStream()) { javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out); image=out.toByteArray(); }
    when(storage.open(anyString())).thenAnswer(a -> new com.aiquestion.storage.ObjectStorage.StoredObject(new java.io.ByteArrayInputStream(image),image.length,"image/png"));
    when(storage.reservePreview(anyString())).thenReturn("eds:training-previews/reserved.png");
    doThrow(new IllegalStateException("storage unavailable")).doNothing().when(storage).putPreview(anyString(),any(byte[].class));
    previews.process(id);
    assertEquals("FAILED",jdbc.queryForObject("SELECT preview_status FROM training_material WHERE id=?",String.class,id));
    trainingMaterials.retryPreview(training,id,owner);
    previews.process(id);
    assertEquals("READY",jdbc.queryForObject("SELECT preview_status FROM training_material WHERE id=?",String.class,id));
    verify(storage,times(1)).reservePreview(anyString());
    verify(storage,times(2)).putPreview(eq("eds:training-previews/reserved.png"),any(byte[].class));
  }

  @Test void anotherWorkerCannotClaimLockedMaterialAndProcessingCannotBeDeleted() throws Exception {
    long training=createTraining(null,false), id=material(training);
    jdbc.update("UPDATE training_material SET preview_status='PROCESSING' WHERE id=?",id);
    try(var session=sqlSessions.openSession(true)) {
      var locks=session.getMapper(MaterialPreviewRepository.class);
      assertEquals(1,locks.acquire(id));
      try { previews.process(id); verify(storage,never()).open(anyString()); }
      finally { locks.release(id); }
    }
    assertThrows(BusinessException.class,()->trainingMaterials.delete(training,id,owner));
  }

  private long material(long training) {
    long id = insert("INSERT INTO training_material(training_id,title,material_type,storage_key,page_count) VALUES(?,'阅读材料','PDF','eds:training-materials/original.pdf',2)", training);
    jdbc.update("UPDATE training_material SET preview_status='READY' WHERE id=?", id);
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
