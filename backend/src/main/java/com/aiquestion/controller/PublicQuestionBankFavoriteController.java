package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * 登录用户的公开题库收藏接口。收藏状态以数据库为准，避免只保存在浏览器导致跨设备丢失。
 */
@Tag(name = "题库收藏", description = "登录用户收藏和取消收藏公开题库")
@RestController
@RequestMapping("/public/bank-favorites")
public class /**
 * 后端业务组件。
 *
 * @author aiquestion
 */
PublicQuestionBankFavoriteController {

    @Operation(summary = "查询当前用户收藏的题库")
    @GetMapping
    public ApiResponse<List<Long>> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.list(authorization);
    }

    @Operation(summary = "收藏题库")
    @PostMapping("/{bankId}")
    public ApiResponse<Void> add(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Long bankId) {
        return service.add(authorization, bankId);
    }

    @Operation(summary = "取消收藏题库")
    @DeleteMapping("/{bankId}")
    public ApiResponse<Void> remove(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable Long bankId) {
        return service.remove(authorization, bankId);
    }

    private final com.aiquestion.service.PublicQuestionBankFavoriteService service;

    public /**
     * 后端业务组件。
     *
     * @author aiquestion
     */
    PublicQuestionBankFavoriteController(com.aiquestion.service.PublicQuestionBankFavoriteService service) {
        this.service = service;
    }
}
