package com.aiquestion.controller;

import com.aiquestion.common.*;
import com.aiquestion.security.JwtTokenService;
import com.aiquestion.service.TrainingService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class TrainingController {

    @GetMapping("/management/trainings/{id}/learning-records")
    public ApiResponse<?> learningRecords(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.learningRecords(id, auth);
    }

    @GetMapping("/management/trainings/{id}/materials")
    public ApiResponse<?> materials(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.materials(id, auth);
    }

    @PutMapping("/management/trainings/{id}/materials/order")
    public ApiResponse<?> reorderMaterials(@PathVariable long id, @RequestBody List<Long> ids, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.reorderMaterials(id, ids, auth);
    }

    @PostMapping(value = "/management/trainings/materials/inspect", consumes = "multipart/form-data")
    public ApiResponse<?> inspectMaterial(@RequestParam org.springframework.web.multipart.MultipartFile file,
        @RequestHeader(value = "Authorization", required = false) String auth) throws java.io.IOException {
        return service.inspectMaterial(file, auth);
    }

    @PostMapping("/management/trainings/{id}/materials")
    public ApiResponse<?> uploadMaterial(@PathVariable long id, @RequestParam org.springframework.web.multipart.MultipartFile file, @RequestHeader(value = "Authorization", required = false) String auth) throws java.io.IOException {
        return service.uploadMaterial(id, file, auth);
    }

    @GetMapping(value = "/management/trainings/{id}/materials/{material}/pages/{page}", produces = "image/png")
    public org.springframework.http.ResponseEntity<byte[]> materialPage(@PathVariable long id, @PathVariable long material, @PathVariable int page, @RequestHeader(value = "Authorization", required = false) String auth) throws java.io.IOException {
        return service.materialPage(id, material, page, auth);
    }

    @PutMapping("/management/trainings/{id}/materials/{material}/timings")
    public ApiResponse<?> materialTimings(@PathVariable long id, @PathVariable long material, @RequestBody List<com.aiquestion.service.TrainingMaterialService.PageTime> times, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.materialTimings(id, material, times, auth);
    }

    @DeleteMapping("/management/trainings/{id}/materials/{material}")
    public ApiResponse<?> deleteMaterial(@PathVariable long id, @PathVariable long material, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.deleteMaterial(id, material, auth);
    }

    public record QuestionDraftRequest(String type) {
    }

    @PostMapping("/management/trainings/question-drafts")
    public ApiResponse<?> createQuestionDraft(@RequestBody QuestionDraftRequest command, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.createQuestionDraft(command, auth);
    }

    @GetMapping("/management/trainings")
    public ApiResponse<?> list(@RequestHeader(value = "Authorization", required = false) String auth) {
        return service.list(auth);
    }

    @GetMapping("/management/trainings/{id}")
    public ApiResponse<?> get(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.get(id, auth);
    }

    @PostMapping("/management/trainings")
    public ApiResponse<?> create(@RequestBody TrainingService.SaveTraining command, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.create(command, auth);
    }

    @PostMapping(value = "/management/trainings/with-materials", consumes = "multipart/form-data")
    public ApiResponse<?> createWithMaterials(@RequestPart("training") TrainingService.SaveTraining command,
        @RequestPart(value = "videos", required = false) List<org.springframework.web.multipart.MultipartFile> videos,
        @RequestPart(value = "materials", required = false) List<org.springframework.web.multipart.MultipartFile> materials,
        @RequestPart(value = "materialTimings", required = false) List<List<com.aiquestion.service.TrainingMaterialService.PageTime>> materialTimings,
        @RequestHeader(value = "Authorization", required = false) String auth) throws java.io.IOException {
        return service.createWithMaterials(command, videos, materials, materialTimings, auth);
    }

    @PutMapping("/management/trainings/{id}")
    public ApiResponse<?> update(@PathVariable long id, @RequestBody TrainingService.SaveTraining command, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.update(id, command, auth);
    }

    @PostMapping("/management/trainings/{id}/end")
    public ApiResponse<?> endTraining(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.endTraining(id, auth);
    }

    @PostMapping("/management/trainings/{id}/exam/end")
    public ApiResponse<?> endExam(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.endExam(id, auth);
    }

    @DeleteMapping("/management/trainings/{id}")
    public ApiResponse<?> delete(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.delete(id, auth);
    }

    @GetMapping("/management/trainings/{id}/responses")
    public ApiResponse<?> responses(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.responses(id, auth);
    }

    @GetMapping("/trainings/{id}/survey")
    public ApiResponse<?> survey(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.survey(id, auth);
    }

    @PostMapping("/trainings/{id}/survey")
    public ApiResponse<?> submit(@PathVariable long id, @RequestBody TrainingService.Submission command, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.submit(id, command, auth);
    }

    @GetMapping("/trainings/{id}")
    public ApiResponse<?> learning(@PathVariable long id, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.learning(id, auth);
    }

    public record Progress(double position, boolean completed) {
    }

    @PutMapping("/trainings/{id}/videos/{video}/progress")
    public ApiResponse<?> progress(@PathVariable long id, @PathVariable long video, @RequestBody Progress data, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.progress(id, video, data, auth);
    }

    @PostMapping("/management/trainings/{id}/videos")
    public ApiResponse<?> upload(@PathVariable long id, @RequestParam("file") org.springframework.web.multipart.MultipartFile file, @RequestHeader(value = "Authorization", required = false) String auth) throws java.io.IOException {
        return service.upload(id, file, auth);
    }

    @GetMapping("/trainings/{id}/videos/{video}/content")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> content(@PathVariable long id, @PathVariable long video, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.content(id, video, auth);
    }

    @DeleteMapping("/management/trainings/{id}/videos/{video}")
    public ApiResponse<?> removeVideo(@PathVariable long id, @PathVariable long video, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.removeVideo(id, video, auth);
    }

    @PutMapping("/management/trainings/{id}/videos/order")
    public ApiResponse<?> reorderVideos(@PathVariable long id, @RequestBody List<Long> ids, @RequestHeader(value = "Authorization", required = false) String auth) {
        return service.reorderVideos(id, ids, auth);
    }

    private final com.aiquestion.service.TrainingEndpointService service;

    public TrainingController(com.aiquestion.service.TrainingEndpointService service) {
        this.service = service;
    }
}
