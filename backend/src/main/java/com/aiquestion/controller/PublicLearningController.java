package com.aiquestion.controller;

import com.aiquestion.common.*;
import com.aiquestion.service.CandidateLearningService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.*;
import java.util.*;

@RestController
@RequestMapping("/public")
public class PublicLearningController {

    @PostMapping("/courses/{id}/complete")
    public ApiResponse<?> courseComplete(@PathVariable long id, @RequestBody Map<String, String> command) {
        return service.courseComplete(id, command);
    }

    @PostMapping("/courses/{id}/learning")
    public ApiResponse<Map<String, Object>> courseLearning(@PathVariable long id, @RequestBody PublicExamController.CandidateCommand command) {
        return service.courseLearning(id, command);
    }

    @GetMapping("/courses/{id}/learning/videos/{video}/content")
    public ResponseEntity<Resource> courseVideo(@PathVariable long id, @PathVariable long video, @RequestParam String token) {
        return service.courseVideo(id, video, token);
    }

    @PutMapping("/courses/{id}/learning/videos/{video}/progress")
    public ApiResponse<?> courseVideoProgress(@PathVariable long id, @PathVariable long video, @RequestBody ProgressCommand c) {
        return service.courseVideoProgress(id, video, c);
    }

    @GetMapping(value = "/courses/{id}/learning/materials/{material}/pages/{page}", produces = "image/png")
    public ResponseEntity<byte[]> coursePage(@PathVariable long id, @PathVariable long material, @PathVariable int page, @RequestParam String token) throws java.io.IOException {
        return service.coursePage(id, material, page, token);
    }

    @PutMapping("/courses/{id}/learning/materials/{material}/pages/{page}/progress")
    public ApiResponse<?> coursePageProgress(@PathVariable long id, @PathVariable long material, @PathVariable int page, @RequestBody Reading c) {
        return service.coursePageProgress(id, material, page, c);
    }

    @GetMapping("/exams/{id}/trainings")
    public ApiResponse<List<Map<String, Object>>> trainings(@PathVariable long id) {
        return service.trainings(id);
    }

    @GetMapping("/trainings/{id}/exam")
    public ApiResponse<Map<String, Object>> exam(@PathVariable long id) {
        return service.exam(id);
    }

    @PostMapping("/exams/{id}/learning")
    public ApiResponse<Map<String, Object>> learning(@PathVariable long id, @RequestBody PublicExamController.CandidateCommand command) {
        return service.learning(id, command);
    }

    @GetMapping("/exams/{id}/learning/videos/{video}/content")
    public ResponseEntity<Resource> content(@PathVariable long id, @PathVariable long video, @RequestParam String token) {
        return service.content(id, video, token);
    }

    @PutMapping("/exams/{id}/learning/videos/{video}/progress")
    public ApiResponse<Map<String, Object>> progress(@PathVariable long id, @PathVariable long video, @RequestBody ProgressCommand command) {
        return service.progress(id, video, command);
    }

    @GetMapping(value = "/exams/{id}/learning/materials/{material}/pages/{page}", produces = "image/png")
    public ResponseEntity<byte[]> page(@PathVariable long id, @PathVariable long material, @PathVariable int page, @RequestParam String token) throws java.io.IOException {
        return service.page(id, material, page, token);
    }

    @PutMapping("/exams/{id}/learning/materials/{material}/pages/{page}/progress")
    public ApiResponse<?> pageProgress(@PathVariable long id, @PathVariable long material, @PathVariable int page, @RequestBody Reading command) {
        return service.pageProgress(id, material, page, command);
    }

    public record Reading(String token, String heartbeatToken, double seconds) {
    }

    public record ProgressCommand(String token, double position, double duration, boolean completed) {
    }

    private final com.aiquestion.service.PublicLearningService service;

    public PublicLearningController(com.aiquestion.service.PublicLearningService service) {
        this.service = service;
    }
}
