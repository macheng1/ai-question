package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.TrainingVideoUploads;
import com.aiquestion.service.PreparedTrainingVideos;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/video-uploads")
public class TrainingVideoUploadController {
    private final TrainingVideoUploads uploads;

    private final PreparedTrainingVideos prepared;

    public TrainingVideoUploadController(TrainingVideoUploads uploads, PreparedTrainingVideos prepared) {
        this.prepared = prepared;
        this.uploads = uploads;
    }

    public record Create(String name, long size) {}

    @PostMapping
    public ApiResponse<?> create(@RequestBody Create request) throws IOException {
        return ApiResponse.ok(Map.of("id", uploads.create(ManagementAccess.userId(), request.name(), request.size())));
    }

    @PostMapping("/{id}/prepare")
    public ApiResponse<?> prepare(@PathVariable String id) {
        prepared.prepare(ManagementAccess.userId(), id);
        return ApiResponse.ok(Map.of("ready", true));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<?> progress(@PathVariable String id) {
        return ApiResponse.ok(prepared.progress(ManagementAccess.userId(), id));
    }

    @PutMapping(value = "/{id}/parts/{index}", consumes = "application/octet-stream")
    public ApiResponse<?> put(@PathVariable String id, @PathVariable int index, HttpServletRequest request) throws IOException {
        uploads.put(ManagementAccess.userId(), id, index, request.getInputStream());
        return ApiResponse.ok(null);
    }
}
