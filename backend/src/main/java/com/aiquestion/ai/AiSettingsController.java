package com.aiquestion.ai;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/system/ai")
public class AiSettingsController {
    private final AiSettingsService settings;

    private final AiChatService chat;

    public AiSettingsController(AiSettingsService settings, AiChatService chat) {
        this.settings = settings;
        this.chat = chat;
    }

    @GetMapping
    public ApiResponse<AiSettingsService.Settings> read() {
        return ApiResponse.ok(settings.read(ManagementAccess.userId()));
    }

    @PostMapping("/test-connection")
    public ApiResponse<AiChatService.ConnectionResult> testConnection() {
        return ApiResponse.ok(chat.testConnection(ManagementAccess.userId()));
    }

    @PutMapping
    public ApiResponse<AiSettingsService.Settings> save(@Valid @RequestBody AiSettingsService.Update request) {
        return ApiResponse.ok(settings.save(ManagementAccess.userId(), request));
    }
}
