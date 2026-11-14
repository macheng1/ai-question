package com.aiquestion.service;

import com.aiquestion.controller.PublicExamController;
import com.aiquestion.common.*;
import com.aiquestion.service.CandidateLearningService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.*;
import java.util.*;
import static com.aiquestion.controller.PublicLearningController.*;
import com.aiquestion.repository.PublicLearningRepository;

@org.springframework.stereotype.Service()
public class PublicLearningService {

    private final CandidateLearningService service;

    private final PublicLearningRepository persistence;

    private final com.aiquestion.service.TrainingMaterialService materials;

    private final com.aiquestion.service.MaterialLearningService reading;

    private final com.aiquestion.service.TrainingVideoStorage storage;

    public PublicLearningService(CandidateLearningService service, PublicLearningRepository persistence, com.aiquestion.service.TrainingVideoStorage storage, com.aiquestion.service.TrainingMaterialService materials, com.aiquestion.service.MaterialLearningService reading) {
        this.reading = reading;
        this.materials = materials;
        this.service = service;
        this.persistence = persistence;
        this.storage = storage;
    }

    public ApiResponse<?> courseComplete(long id, Map<String, String> command) {
        if (id <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        var identity = reading.session(-id, command.get("token"));
        service.requireComplete(-id, identity.get("candidate_name").toString(), identity.get("phone").toString());
        return ApiResponse.ok(Map.of("completed", true));
    }

    public ApiResponse<Map<String, Object>> courseLearning(long id, PublicExamController.CandidateCommand command) {
        if (id <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        return ApiResponse.ok(service.learning(-id, command.name(), command.phone()));
    }

    public ResponseEntity<Resource> courseVideo(long id, long video, String token) {
        if (id <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        return storage.content(service.video(-id, video, token));
    }

    public ApiResponse<?> courseVideoProgress(long id, long video, ProgressCommand c) {
        if (id <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        return ApiResponse.ok(service.progress(-id, video, c.token(), c.position(), c.duration(), c.completed(), c.requestId()));
    }

    public ResponseEntity<?> coursePage(long id, long material, int page, String token, boolean direct) throws java.io.IOException {
        if (id <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        reading.authorize(-id, material, token, page);
        return materials.learnerPage(id, material, page, direct);
    }

    public ApiResponse<?> coursePageProgress(long id, long material, int page, Reading c) {
        if (id <= 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        return ApiResponse.ok(reading.heartbeat(-id, material, page, c.token(), c.heartbeatToken(), c.seconds()));
    }

    public ApiResponse<List<Map<String, Object>>> trainings(long id) {
        return ApiResponse.ok(service.trainings(id));
    }

    public ApiResponse<Map<String, Object>> exam(long id) {
        var rows = persistence.findTrainingEntry(id);
        if (rows.isEmpty())
            throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
        return ApiResponse.ok(rows.get(0));
    }

    public ApiResponse<Map<String, Object>> learning(long id, PublicExamController.CandidateCommand command) {
        return ApiResponse.ok(service.learning(id, command.name(), command.phone()));
    }

    public ResponseEntity<Resource> content(long id, long video, String token) {
        var row = service.video(id, video, token);
        return storage.content(row);
    }

    public ApiResponse<Map<String, Object>> progress(long id, long video, ProgressCommand command) {
        return ApiResponse.ok(service.progress(id, video, command.token(), command.position(), command.duration(), command.completed(), command.requestId()));
    }

    public ResponseEntity<?> page(long id, long material, int page, String token, boolean direct) throws java.io.IOException {
        reading.authorize(id, material, token, page);
        long training = com.aiquestion.repository.RepositoryRows.one(persistence.findMaterialTraining(material));
        return materials.learnerPage(training, material, page, direct);
    }

    public ApiResponse<?> pageProgress(long id, long material, int page, Reading command) {
        return ApiResponse.ok(reading.heartbeat(id, material, page, command.token(), command.heartbeatToken(), command.seconds()));
    }
}
