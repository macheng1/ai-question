package com.aiquestion.service;

import java.util.*;
import org.springframework.stereotype.Service;
import com.aiquestion.repository.TrainingLogRepository;

@Service
public class TrainingLogService {

    private final TrainingLogRepository persistence;

    private final TrainingService trainings;

    public TrainingLogService(TrainingLogRepository persistence, TrainingService trainings) {
        this.persistence = persistence;
        this.trainings = trainings;
    }

    /**
     * 提醒名单只需完成状态，批量查询计数，避免逐人加载材料与答卷明细。
     */
    public Set<String> completedParticipants(long id, long user) {
        trainings.get(id, user);
        long total = com.aiquestion.repository.RepositoryRows.one(persistence.countTrainingUnits(id, id));
        var completed = new HashSet<String>();
        for (var session : persistence.findCandidateCompletion(id)) {
            if ("COMPLETED".equals(LearnerTaskStatus.learning(true, total, ((Number) session.get("completed_parts")).longValue(), session.get("completed_at") != null))) {
                completed.add(session.get("phone") + "|" + session.get("candidate_name"));
            }
        }
        return completed;
    }

    public List<Map<String, Object>> list(long id, long user) {
        trainings.get(id, user);
        var sessions = persistence.findLearningSessions(id);
        for (var s : sessions) {
            var videos = persistence.findVideoLogs(com.aiquestion.repository.RepositoryRows.nullableLong(s.get("id")), id);
            var pages = persistence.findMaterialLogs(com.aiquestion.repository.RepositoryRows.nullableLong(s.get("id")), id);
            var parts = new ArrayList<Map<String, Object>>();
            parts.addAll(videos);
            parts.addAll(pages);
            s.put("materials", parts);
            s.put("studiedSeconds", parts.stream().filter(p -> p.get("studiedSeconds") instanceof Number).mapToDouble(p -> ((Number) p.get("studiedSeconds")).doubleValue()).sum());
            long completedParts = parts.stream().filter(p -> Boolean.TRUE.equals(p.get("completed")) || "1".equals(String.valueOf(p.get("completed")))).count();
            s.put("completed", "COMPLETED".equals(LearnerTaskStatus.learning(true, parts.size(), (int) completedParts, s.get("completedAt") != null)));
            s.put("attempts", persistence.findCandidateAttempts(com.aiquestion.repository.RepositoryRows.nullableLong(s.get("taskId")), (String) s.get("phone"), (String) s.get("name")));
        }
        return sessions;
    }
}
