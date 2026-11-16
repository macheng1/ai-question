package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.TrainingVideoUploads;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/video-uploads")
public class TrainingVideoUploadController {
    private final TrainingVideoUploads uploads;

    public TrainingVideoUploadController(TrainingVideoUploads uploads) {
        this.uploads = uploads;
    }

    public record Create(String name, long size) {}

    @PostMapping
    public ApiResponse<?> create(@RequestBody Create request) throws IOException {
        return ApiResponse.ok(Map.of("id", uploads.create(ManagementAccess.userId(), request.name(), request.size())));
    }

    @PutMapping(value = "/{id}/parts/{index}", consumes = "application/octet-stream")
    public ApiResponse<?> put(@PathVariable String id, @PathVariable int index, HttpServletRequest request) throws IOException {
        uploads.put(ManagementAccess.userId(), id, index, request.getInputStream());
        return ApiResponse.ok(null);
    }
}
