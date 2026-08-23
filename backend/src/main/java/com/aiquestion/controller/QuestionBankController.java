package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.PageQuery;
import com.aiquestion.common.PageResult;
import com.aiquestion.domain.QuestionBank;
import com.aiquestion.service.QuestionBankService;
import com.aiquestion.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** 题库管理接口，后续可通过权限注解接入管理员角色。 */
@Tag(name = "题库管理")
@RestController
@RequestMapping("/management/banks")
public class QuestionBankController {
    private final QuestionBankService service;
    private final JwtTokenService tokens;
    public QuestionBankController(QuestionBankService service, JwtTokenService tokens) { this.service = service; this.tokens = tokens; }

    @Operation(summary = "分页查询题库")
    @GetMapping
    public ApiResponse<PageResult<QuestionBank>> page(@Valid PageQuery query, @RequestHeader(value="Authorization", required=false) String authorization) {
        return ApiResponse.ok(service.page(query, userId(authorization)));
    }

    @Operation(summary = "新建题库")
    @PostMapping
    public ApiResponse<QuestionBank> create(@Valid @RequestBody SaveQuestionBankRequest request, @RequestHeader(value="Authorization", required=false) String authorization) {
        return ApiResponse.ok(service.create(request.name(), request.category(), request.source(), request.published(), userId(authorization)));
    }

    @Operation(summary = "修改题库")
    @PutMapping("/{id}")
    public ApiResponse<QuestionBank> update(@PathVariable Long id, @Valid @RequestBody SaveQuestionBankRequest request, @RequestHeader(value="Authorization", required=false) String authorization) {
        return ApiResponse.ok(service.update(id, request.name(), request.category(), request.source(), request.published(), userId(authorization)));
    }

    @Operation(summary = "删除题库")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestHeader(value="Authorization", required=false) String authorization) {
        service.delete(id, userId(authorization));
        return ApiResponse.ok(null);
    }

    private Long userId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new com.aiquestion.common.BusinessException(com.aiquestion.common.ErrorCode.UNAUTHORIZED);
        try { return Long.parseLong(tokens.subject(authorization.substring(7), false)); }
        catch (RuntimeException ex) { throw new com.aiquestion.common.BusinessException(com.aiquestion.common.ErrorCode.INVALID_TOKEN); }
    }

    public record SaveQuestionBankRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String category,
            @Size(max = 255) String source,
            Boolean published) {}
}
