package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/overview")
public class ManagementOverviewController {

    @GetMapping
    public ApiResponse<?> overview() {
        return service.overview();
    }

    private final com.aiquestion.service.ManagementOverviewService service;

    public ManagementOverviewController(com.aiquestion.service.ManagementOverviewService service) {
        this.service = service;
    }
}
