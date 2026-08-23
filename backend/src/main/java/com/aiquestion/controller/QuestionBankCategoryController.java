package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.domain.QuestionBankCategory;
import com.aiquestion.service.QuestionBankCategoryService;
import com.aiquestion.security.JwtTokenService;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name="题库分类管理")
@RestController
@RequestMapping("/management/bank-categories")
public class QuestionBankCategoryController {
    private final QuestionBankCategoryService service;
    private final JwtTokenService jwtTokenService;
    public QuestionBankCategoryController(QuestionBankCategoryService service,JwtTokenService jwtTokenService){this.service=service;this.jwtTokenService=jwtTokenService;}
    @Operation(summary="查询分类树") @GetMapping public ApiResponse<List<QuestionBankCategory>> list(@RequestHeader(value="Authorization",required=false) String authorization){return ApiResponse.ok(service.list(userId(authorization)));}
    @Operation(summary="新建分类") @PostMapping public ApiResponse<QuestionBankCategory> create(@Valid @RequestBody SaveCategoryRequest request,@RequestHeader(value="Authorization",required=false) String authorization){return ApiResponse.ok(service.create(request.name(),request.parentId(),request.description(),request.sortNo(),userId(authorization)));}
    @Operation(summary="修改分类") @PutMapping("/{id}") public ApiResponse<QuestionBankCategory> update(@PathVariable Long id,@Valid @RequestBody SaveCategoryRequest request,@RequestHeader(value="Authorization",required=false) String authorization){return ApiResponse.ok(service.update(id,request.name(),request.parentId(),request.description(),request.sortNo(),userId(authorization)));}
    @Operation(summary="删除分类") @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String authorization){service.delete(id,userId(authorization));return ApiResponse.ok(null);}
    private Long userId(String authorization){
        if(authorization==null||!authorization.startsWith("Bearer ")) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        try{return Long.parseLong(jwtTokenService.subject(authorization.substring(7),false));}
        catch(RuntimeException ex){throw new BusinessException(ErrorCode.INVALID_TOKEN);}
    }
    public record SaveCategoryRequest(@NotBlank @Size(max=80) String name, Long parentId, @Size(max=255) String description, Integer sortNo){}
}
