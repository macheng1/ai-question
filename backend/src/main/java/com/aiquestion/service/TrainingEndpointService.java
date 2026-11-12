package com.aiquestion.service;

import com.aiquestion.controller.TrainingController;
import com.aiquestion.common.*;
import com.aiquestion.security.JwtTokenService;
import com.aiquestion.service.TrainingService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.aiquestion.controller.TrainingController.*;
import com.aiquestion.repository.TrainingAccessRepository;

@org.springframework.stereotype.Service()
public class TrainingEndpointService {

    private final TrainingService service;

    private final com.aiquestion.service.AccountService accounts;

    private final com.aiquestion.service.TrainingLogService logs;

    private final com.aiquestion.service.TrainingMaterialService materials;

    private final JwtTokenService tokens;

    private final TrainingAccessRepository persistence;

    private final com.aiquestion.service.TrainingVideoStorage storage;

    public TrainingEndpointService(com.aiquestion.service.AccountService accounts, TrainingService service, JwtTokenService tokens, TrainingAccessRepository persistence, com.aiquestion.service.TrainingVideoStorage storage, com.aiquestion.service.TrainingMaterialService materials, com.aiquestion.service.TrainingLogService logs) {
        this.accounts = accounts;
        this.logs = logs;
        this.materials = materials;
        this.service = service;
        this.tokens = tokens;
        this.persistence = persistence;
        this.storage = storage;
    }

    private long user(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        long id;
        try {
            id = Long.parseLong(tokens.subject(authorization.substring(7), false));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countActiveAdministrator(id)) == 0)
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return id;
    }

    public ApiResponse<?> learningRecords(long id, String auth) {
        return ApiResponse.ok(logs.list(id, user(auth)));
    }

    public ApiResponse<?> materials(long id, String auth) {
        service.get(id, user(auth));
        return ApiResponse.ok(materials.list(id));
    }

    public ApiResponse<?> reorderMaterials(long id, List<Long> ids, String auth) {
        materials.reorder(id, user(auth), ids);
        return ApiResponse.ok(null);
    }

    public ApiResponse<?> uploadMaterial(long id, org.springframework.web.multipart.MultipartFile file, String auth) throws java.io.IOException {
        materials.upload(id, user(auth), file);
        return ApiResponse.ok(materials.list(id));
    }

    public org.springframework.http.ResponseEntity<byte[]> materialPage(long id, long material, int page, String auth) throws java.io.IOException {
        service.get(id, user(auth));
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control", "private, no-store").body(materials.page(id, material, page));
    }

    public ApiResponse<?> materialTimings(long id, long material, List<com.aiquestion.service.TrainingMaterialService.PageTime> times, String auth) {
        materials.timings(id, material, user(auth), times);
        return ApiResponse.ok(null);
    }

    public ApiResponse<?> deleteMaterial(long id, long material, String auth) {
        materials.delete(id, material, user(auth));
        return ApiResponse.ok(null);
    }

    public ApiResponse<?> createQuestionDraft(QuestionDraftRequest command, String auth) {
        user(auth);
        return ApiResponse.ok(service.createQuestionDraft(command.type()));
    }

    public ApiResponse<?> list(String auth) {
        return ApiResponse.ok(service.list(user(auth)));
    }

    public ApiResponse<?> get(long id, String auth) {
        return ApiResponse.ok(service.get(id, user(auth)));
    }

    public ApiResponse<?> create(TrainingService.SaveTraining command, String auth) {
        return ApiResponse.ok(service.save(null, command, user(auth)));
    }

    public ApiResponse<?> update(long id, TrainingService.SaveTraining command, String auth) {
        return ApiResponse.ok(service.save(id, command, user(auth)));
    }

    public ApiResponse<?> delete(long id, String auth) {
        long uid = user(auth);
        var keys = service.videoFiles(id, uid);
        service.delete(id, uid);
        keys.forEach(this::cleanupVideo);
        return ApiResponse.ok(null);
    }

    public ApiResponse<?> responses(long id, String auth) {
        return ApiResponse.ok(service.responses(id, user(auth)));
    }

    public ApiResponse<?> survey(long id, String auth) {
        var identity = accounts.current(auth);
        return ApiResponse.ok(identity.accountType() == com.aiquestion.security.AccountType.EMPLOYEE ? service.employeeSurvey(id, accounts.employee(identity.accountId())) : service.survey(id, identity.accountId()));
    }

    public ApiResponse<?> submit(long id, TrainingService.Submission command, String auth) {
        var identity = accounts.current(auth);
        if (identity.accountType() == com.aiquestion.security.AccountType.EMPLOYEE)
            service.submitEmployee(id, command, accounts.employee(identity.accountId()));
        else
            service.submit(id, command, identity.accountId());
        return ApiResponse.ok(null);
    }

    public ApiResponse<?> learning(long id, String auth) {
        return ApiResponse.ok(service.learning(id, user(auth)));
    }

    public ApiResponse<?> progress(long id, long video, Progress data, String auth) {
        service.progress(id, video, user(auth), data.position(), data.completed());
        return ApiResponse.ok(null);
    }

    public ApiResponse<?> upload(long id, org.springframework.web.multipart.MultipartFile file, String auth) throws java.io.IOException {
        long uid = user(auth);
        service.get(id, uid);
        String name = Objects.toString(file.getOriginalFilename(), "");
        boolean mp4 = name.toLowerCase(Locale.ROOT).endsWith(".mp4"), webm = name.toLowerCase(Locale.ROOT).endsWith(".webm");
        if ((!mp4 && !webm) || file.getSize() < 12 || file.getSize() > 100L * 1024 * 1024)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持100MB以内的MP4或WebM视频");
        byte[] head;
        try (var stream = file.getInputStream()) {
            head = stream.readNBytes(12);
        }
        boolean valid = mp4 ? head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p' : (head[0] & 255) == 0x1a && (head[1] & 255) == 0x45 && (head[2] & 255) == 0xdf && (head[3] & 255) == 0xa3;
        if (!valid)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频文件内容与扩展名不匹配");
        String type = mp4 ? "video/mp4" : "video/webm";
        String key = storage.upload(file, type);
        try {
            service.addVideo(id, uid, name.substring(0, Math.min(name.length(), 255)), key, type);
        } catch (RuntimeException e) {
            cleanupVideo(key);
            throw e;
        }
        return ApiResponse.ok(service.learning(id, uid));
    }

    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> content(long id, long video, String auth) {
        service.get(id, user(auth));
        return storage.content(service.video(id, video));
    }

    public ApiResponse<?> removeVideo(long id, long video, String auth) {
        String key = service.removeVideo(id, video, user(auth));
        cleanupVideo(key);
        return ApiResponse.ok(null);
    }

    public ApiResponse<?> reorderVideos(long id, List<Long> ids, String auth) {
        service.reorderVideos(id, user(auth), ids);
        return ApiResponse.ok(null);
    }

    private void cleanupVideo(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(TrainingController.class).warn("Training video cleanup failed for storage key {}", key, error);
        }
    }
}
