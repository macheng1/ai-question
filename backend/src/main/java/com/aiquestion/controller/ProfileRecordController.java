package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/**
 * 当前登录人的考试记录与错题查询接口。正式记录只读数据库，不依赖浏览器缓存。
 */
@Tag(name = "个人记录")
@RestController
@RequestMapping("/profile")
public class /**
 * 后端业务组件。
 *
 * @author aiquestion
 */
ProfileRecordController {

    @Operation(summary = "查询我的考试记录")
    @GetMapping("/records")
    public ApiResponse<List<Map<String, Object>>> records(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.records(authorization);
    }

    @Operation(summary = "查询我的错题")
    @GetMapping("/mistakes")
    public ApiResponse<List<Map<String, Object>>> mistakes(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.mistakes(authorization);
    }


    private final com.aiquestion.service.ProfileRecordService service;

    public /**
     * 后端业务组件。
     *
     * @author aiquestion
     */
    ProfileRecordController(com.aiquestion.service.ProfileRecordService service) {
        this.service = service;
    }
}
