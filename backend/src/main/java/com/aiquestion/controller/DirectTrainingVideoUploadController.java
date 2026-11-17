package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.DirectTrainingVideoUploads;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/video-uploads/direct")
public class DirectTrainingVideoUploadController {
    private final DirectTrainingVideoUploads uploads;
    public DirectTrainingVideoUploadController(DirectTrainingVideoUploads uploads) { this.uploads = uploads; }

    @PostMapping
    public ApiResponse<?> create(@RequestBody TrainingVideoUploadController.Create request) {
        return ApiResponse.ok(uploads.create(ManagementAccess.userId(), request.name(), request.size()));
    }

    @PostMapping("/{id}/parts/{index}")
    public ApiResponse<?> sign(@PathVariable String id, @PathVariable int index) {
        return ApiResponse.ok(Map.of("url", uploads.sign(ManagementAccess.userId(), id, index)));
    }

    @PutMapping(value = "/{id}/parts/{index}", consumes = "application/octet-stream")
    public ApiResponse<?> put(@PathVariable String id, @PathVariable int index, HttpServletRequest request) throws IOException {
        uploads.put(ManagementAccess.userId(), id, index, request.getContentLengthLong(), request.getInputStream());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<?> complete(@PathVariable String id) {
        uploads.complete(ManagementAccess.userId(), id);
        return ApiResponse.ok(Map.of("ready", true));
    }

    // SDK error bodies may echo a signing request. Keep them out of logs and API responses.
    @ExceptionHandler({com.aliyun.oss.OSSException.class, com.aliyun.oss.ClientException.class,
            com.amazonaws.SdkBaseException.class})
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_GATEWAY)
    public ApiResponse<?> storageFailure(RuntimeException error) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Video multipart storage request failed ({})",
                error.getClass().getSimpleName());
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR, "对象存储请求失败，请重试");
    }
}
