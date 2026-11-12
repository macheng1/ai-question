package com.aiquestion.service;

import com.aiquestion.common.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aiquestion.repository.TrainingRepository;

@Service
public class TrainingService {

    private final TrainingRepository persistence;

    private final ObjectMapper json;

    public TrainingService(TrainingRepository persistence, ObjectMapper json, AssessmentRosterService assessmentRosterService, TrainingAudienceService trainingAudienceService) {
        this.persistence = persistence;
        this.json = json;
        this.assessmentRosterService = assessmentRosterService;
        this.trainingAudienceService = trainingAudienceService;
    }

    public record SaveTraining(String name, String description, boolean surveyEnabled, List<TrainingSurveyRules.Question> questions, Integer version, Long assessmentId, String category, String requirementType, TrainingAudienceService.Scope audience, java.time.LocalDateTime endAt, java.time.LocalDateTime startAt) {
    }

    public record Submission(Integer version, Map<String, String> answers) {
    }

    public TrainingSurveyRules.Question createQuestionDraft(String type) {
        if (type == null || !Set.of("RATING", "SINGLE", "TEXT").contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的评估题型");
        }
        return new TrainingSurveyRules.Question(UUID.randomUUID().toString(), type, "", true, "SINGLE".equals(type) ? List.of("满意", "一般") : List.of());
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<TrainingSurveyRules.Question> questions(Map<String, Object> row) {
        try {
            return json.readValue(row.get("questions_json").toString(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> row(long id, boolean lock) {
        var rows = persistence.findById(id, lock);
        if (rows.isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
        return rows.get(0);
    }

    private void owner(Map<String, Object> row, long user) {
        if (((Number) row.get("created_by")).longValue() != user)
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
    }

    private boolean enabled(Map<String, Object> row) {
        return Boolean.TRUE.equals(row.get("survey_enabled")) || "1".equals(String.valueOf(row.get("survey_enabled")));
    }

    private long count(long id) {
        return com.aiquestion.repository.RepositoryRows.one(persistence.countSurveyResponses(id));
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.get("id"));
        data.put("name", row.get("name"));
        data.put("description", row.get("description"));
        data.put("purpose", row.get("assessment_id") == null ? "STANDALONE" : "EXAM");
        data.put("category", row.get("category"));
        data.put("requirementType", row.get("requirement_type"));
        data.put("assessmentId", row.get("assessment_id"));
        data.put("startAt", row.get("start_at") instanceof java.sql.Timestamp time ? time.toLocalDateTime() : row.get("start_at"));
        data.put("endAt", row.get("end_at") instanceof java.sql.Timestamp time ? time.toLocalDateTime() : row.get("end_at"));
        data.put("surveyEnabled", enabled(row));
        data.put("questions", questions(row));
        data.put("version", row.get("version"));
        return data;
    }

    public List<Map<String, Object>> list(long user) {
        return persistence.findByOwner(user).stream().map(r -> {
            var v = view(r);
            v.put("responseCount", count(((Number) r.get("id")).longValue()));
            return v;
        }).toList();
    }

    public Map<String, Object> get(long id, long user) {
        var r = row(id, false);
        owner(r, user);
        var v = view(r);
        v.put("responseCount", count(id));
        v.put("audience", trainingAudienceService.get(id, user));
        v.put("learningLocked", (r.get("assessment_id") instanceof Number task && com.aiquestion.repository.RepositoryRows.one(persistence.countExamParticipants(task.longValue())) > 0) || com.aiquestion.repository.RepositoryRows.one(persistence.countTrainingLearningSessions(id)) > 0);
        return v;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> save(Long id, SaveTraining command, long user) {
        if (command.startAt() != null && command.endAt() != null && !command.endAt().isAfter(command.startAt()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "培训结束时间必须晚于开始时间");
        if (command.name() == null || command.name().isBlank() || command.name().length() > 160 || (command.description() != null && command.description().length() > 2000))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请检查培训名称和简介长度");
        trainingAudienceService.lockAllocationOwner(user);
        if (command.assessmentId() != null && com.aiquestion.repository.RepositoryRows.one(persistence.countOwnedAssessments(command.assessmentId(), user)) == 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能关联自己创建的考试");
        // Use the same task-before-training lock order as starting learning or an exam.
        var assessmentLocks = new TreeSet<Long>();
        Long previousAssessment = null;
        Long copiedAssessment = null;
        boolean rebuildAudience = true;
        if (id != null) {
            var existing = row(id, false);
            owner(existing, user);
            previousAssessment = assessmentId(existing);
            if (previousAssessment != null)
                assessmentLocks.add(previousAssessment);
        }
        if (command.assessmentId() != null)
            assessmentLocks.add(command.assessmentId());
        for (long task : assessmentLocks) assessmentRosterService.lockTask(task);
        String purpose = command.assessmentId() == null ? "STANDALONE" : "EXAM";
        if (command.assessmentId() != null) {
            if (com.aiquestion.repository.RepositoryRows.one(persistence.countOtherTrainingReferences(command.assessmentId(), id == null ? 0 : id)) > 0)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该考试已关联其他培训");
        }
        if (command.assessmentId() != null) {
            boolean associationChanged = id == null || !Objects.equals(row(id, false).get("assessment_id"), command.assessmentId());
            if (associationChanged && com.aiquestion.repository.RepositoryRows.one(persistence.countAssessmentActivity(command.assessmentId(), command.assessmentId())) > 0)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该考试已有学习或答题记录，不能新增关联培训");
        }
        String category = Objects.toString(command.category(), "UNCATEGORIZED");
        String requirement = Objects.toString(command.requirementType(), "REQUIRED");
        if (!Set.of("UNCATEGORIZED", "ONBOARDING", "SAFETY", "POLICY").contains(category) || !Set.of("REQUIRED", "ELECTIVE").contains(requirement))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "培训分类或学习要求无效");
        TrainingSurveyRules.questions(command.questions());
        if (command.surveyEnabled() && command.questions().isEmpty())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开启评估时至少添加一道题");
        if (id == null) {
            var inserted1 = new com.aiquestion.repository.GeneratedId();
            persistence.insertTraining(command.name().trim(), Objects.toString(command.description(), ""), command.surveyEnabled(), encode(command.questions()), user, inserted1);
            id = inserted1.getId();
        } else {
            var old = row(id, true);
            owner(old, user);
            Long oldAssessment = assessmentId(old);
            if (!Objects.equals(previousAssessment, oldAssessment))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "培训关联考试已变更，请刷新后重试");
            if (!Objects.equals(oldAssessment, command.assessmentId()))
                copiedAssessment = copiedAssessmentId(old);
            if ((!Objects.equals(oldAssessment, command.assessmentId())) && com.aiquestion.repository.RepositoryRows.one(persistence.countLearningBeforeRebind(id, oldAssessment)) > 0)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能变更关联考试");
            if ((!Objects.equals(oldAssessment, command.assessmentId())) && oldAssessment != null && com.aiquestion.repository.RepositoryRows.one(persistence.countParticipantsBeforeRebind(oldAssessment)) > 0)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已有考试记录，不能变更关联考试");
            if (!Objects.equals(command.version(), ((Number) old.get("version")).intValue()))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "培训已被修改，请刷新后重试");
            if (count(id) > 0 && !questions(old).equals(command.questions()))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已有反馈，不能修改题目；请新建培训");
            var scope = command.audience();
            var originalScope = trainingAudienceService.get(id, user);
            if (scope != null && scope.type() != null && scope.ids() != null && scope.ids().stream().noneMatch(Objects::isNull)
                    && originalScope != null && Objects.equals(oldAssessment, command.assessmentId())
                    && Objects.equals(scope.type(), originalScope.type())
                    && new TreeSet<>(scope.ids()).equals(new TreeSet<>(originalScope.ids()))
                    && ("ALL".equals(scope.type()) || !scope.ids().isEmpty()))
                rebuildAudience = false;
            persistence.updateContent(command.name().trim(), Objects.toString(command.description(), ""), command.surveyEnabled(), encode(command.questions()), id);
        }
        persistence.updateSettings(command.assessmentId(), category, requirement, purpose, command.endAt(), command.startAt(), id);
        if (rebuildAudience)
            trainingAudienceService.save(id, user, command.audience());
        if (copiedAssessment != null) {
            persistence.clearCopiedAssessment(id);
            deleteCopiedAssessment(copiedAssessment, user);
        }
        return get(id, user);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void delete(long id, long user) {
        var existing = row(id, false);
        owner(existing, user);
        Long previousAssessment = assessmentId(existing);
        if (previousAssessment != null)
            assessmentRosterService.lockTask(previousAssessment);
        var r = row(id, true);
        owner(r, user);
        if (!Objects.equals(previousAssessment, assessmentId(r)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "培训关联考试已变更，请刷新后重试");
        Integer notStarted = com.aiquestion.repository.RepositoryRows.one(persistence.countUpcomingTrainings(id));
        if (!Integer.valueOf(1).equals(notStarted))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有未开始的培训可以删除");
        if (previousAssessment != null && com.aiquestion.repository.RepositoryRows.one(persistence.countParticipantsBeforeDelete(previousAssessment)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有考试记录，不能删除培训");
        if (count(id) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有反馈，不能删除培训，可关闭评估");
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countSessionsBeforeDelete(id)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能删除培训");
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countMaterials(id)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先删除培训材料，再删除培训");
        persistence.deleteTraining(id);
        Long copiedAssessment = copiedAssessmentId(r);
        if (copiedAssessment != null)
            deleteCopiedAssessment(copiedAssessment, user);
    }

    private Long assessmentId(Map<String, Object> training) {
        return training.get("assessment_id") instanceof Number n ? n.longValue() : null;
    }

    private Long copiedAssessmentId(Map<String, Object> training) {
        Long copied = training.get("copied_assessment_id") instanceof Number n ? n.longValue() : null;
        // Only the exam created for this copy may be removed; never infer ownership from its name.
        if (copied != null && !Objects.equals(copied, assessmentId(training)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "副本考试关联异常，请检查后重试");
        return copied;
    }

    private void deleteCopiedAssessment(long task, long user) {
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countAssessmentReferences(task)) > 0 || com.aiquestion.repository.RepositoryRows.one(persistence.countCopiedExamParticipants(task)) > 0 || com.aiquestion.repository.RepositoryRows.one(persistence.countCopiedExamSessions(task)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "副本考试已被使用，不能自动删除");
        persistence.deleteOwnedAssessment(task, user);
    }

    private void requireLearner(long id, long user) {
        var r = row(id, false);
        if (((Number) r.get("created_by")).longValue() == user)
            return;
        var person = com.aiquestion.repository.RepositoryRows.one(persistence.findAdministratorIdentity(user));
        trainingAudienceService.require(id, person.get("name").toString(), person.get("phone").toString());
    }

    public Map<String, Object> survey(long id, long user) {
        requireLearner(id, user);
        return surveyView(id, "user_id", user);
    }

    public Map<String, Object> employeeSurvey(long id, com.aiquestion.service.AccountService.Profile employee) {
        requireEmployeeSurvey(id, employee);
        return surveyView(id, "employee_id", employee.userId());
    }

    private void requireEmployeeSurvey(long id, com.aiquestion.service.AccountService.Profile employee) {
        var course = row(id, false);
        if (((Number) course.get("created_by")).longValue() != employee.enterpriseId())
            throw new BusinessException(ErrorCode.FORBIDDEN);
        trainingAudienceService.require(id, employee.name(), employee.phone());
    }

    private Map<String, Object> surveyView(long id, String identityColumn, long accountId) {
        var r = row(id, false);
        if (!enabled(r))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "此培训尚未开启或已关闭评估");
        var result = view(r);
        result.put("submitted", submitted(id, identityColumn, accountId));
        return result;
    }

    private boolean submitted(long id, String identityColumn, long accountId) {
        return com.aiquestion.repository.RepositoryRows.one(persistence.countSubmittedSurvey(id, accountId, "employee_id".equals(identityColumn))) > 0;
    }

    @Transactional
    public void submit(long id, Submission command, long user) {
        requireLearner(id, user);
        saveSurvey(id, command, "user_id", user);
    }

    @Transactional
    public void submitEmployee(long id, Submission command, com.aiquestion.service.AccountService.Profile employee) {
        requireEmployeeSurvey(id, employee);
        saveSurvey(id, command, "employee_id", employee.userId());
    }

    private void saveSurvey(long id, Submission command, String identityColumn, long accountId) {
        var r = row(id, true);
        if (!enabled(r))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评估已关闭");
        if (!Objects.equals(command.version(), ((Number) r.get("version")).intValue()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评估内容已更新，请刷新页面后填写");
        TrainingSurveyRules.answers(questions(r), command.answers());
        if (submitted(id, identityColumn, accountId))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "你已提交过本次评估");
        persistence.insertSurveyResponse(id, accountId, encode(command.answers()), "employee_id".equals(identityColumn));
    }

    public List<Map<String, Object>> responses(long id, long user) {
        owner(row(id, false), user);
        return persistence.findSurveyResponses(id).stream().map(r -> {
            try {
                r.put("answers", json.readValue(r.remove("answers_json").toString(), new TypeReference<Map<String, String>>() {
                }));
                return r;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    public Map<String, Object> learning(long id, long user) {
        requireLearner(id, user);
        var data = view(row(id, false));
        data.put("videos", persistence.findAdministratorVideoProgress(user, id));
        return data;
    }

    public void progress(long id, long video, long user, double position, boolean completed) {
        requireLearner(id, user);
        video(id, video);
        if (!Double.isFinite(position) || position < 0 || position > 86400)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        persistence.saveAdministratorVideoProgress(video, user, position, completed);
    }

    public Map<String, Object> video(long id, long video) {
        var rows = persistence.findVideo(id, video);
        if (rows.isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND);
        return rows.get(0);
    }

    @Transactional
    public void addVideo(long id, long user, String title, String path, String type) {
        owner(row(id, true), user);
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countSessionsBeforeAddingVideo(id)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能新增视频，请新建培训");
        persistence.insertVideo(id, title, path, type);
    }

    @Transactional
    public String removeVideo(long id, long video, long user) {
        owner(row(id, true), user);
        var data = video(id, video);
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countCandidateVideoProgress(video)) > 0 || com.aiquestion.repository.RepositoryRows.one(persistence.countAdministratorVideoProgress(video)) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能删除视频");
        persistence.deleteVideo(video, id);
        return data.get("storage_path").toString();
    }

    @Transactional
    public void reorderVideos(long id, long user, List<Long> ids) {
        owner(row(id, true), user);
        var current = persistence.findVideoIds(id);
        if (ids == null || ids.stream().anyMatch(Objects::isNull) || ids.size() != current.size() || new HashSet<>(ids).size() != ids.size() || !new HashSet<>(ids).equals(new HashSet<>(current)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频列表已变更，请刷新后重新排序");
        for (int i = 0; i < ids.size(); i++) persistence.updateVideoOrder(i, id, ids.get(i));
    }

    public List<String> videoFiles(long id, long user) {
        owner(row(id, false), user);
        return persistence.findVideoStoragePaths(id);
    }

    private final AssessmentRosterService assessmentRosterService;

    private final TrainingAudienceService trainingAudienceService;
}
