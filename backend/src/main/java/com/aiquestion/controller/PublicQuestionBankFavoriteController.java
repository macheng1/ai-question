package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** 登录用户的公开题库收藏接口。收藏状态以数据库为准，避免只保存在浏览器导致跨设备丢失。 */
@Tag(name = "题库收藏", description = "登录用户收藏和取消收藏公开题库")
@RestController
@RequestMapping("/public/bank-favorites")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class PublicQuestionBankFavoriteController {
  private final JdbcTemplate jdbc;
  private final JwtTokenService tokens;

  public PublicQuestionBankFavoriteController(JdbcTemplate jdbc, JwtTokenService tokens) {
    this.jdbc = jdbc;
    this.tokens = tokens;
  }

  @Operation(summary = "查询当前用户收藏的题库")
  @GetMapping
  public ApiResponse<List<Long>> list(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    long userId = userId(authorization);
    return ApiResponse.ok(
        jdbc.queryForList(
            "SELECT bank_id FROM user_question_bank_favorite WHERE user_id=? ORDER BY created_at DESC",
            Long.class,
            userId));
  }

  @Operation(summary = "收藏题库")
  @PostMapping("/{bankId}")
  public ApiResponse<Void> add(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable Long bankId) {
    long userId = userId(authorization);
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM question_bank WHERE id=? AND published=TRUE",
            Integer.class,
            bankId)
        == 0) {
      throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
    }
    jdbc.update(
        "INSERT IGNORE INTO user_question_bank_favorite(user_id,bank_id) VALUES(?,?)",
        userId,
        bankId);
    return ApiResponse.ok(null);
  }

  @Operation(summary = "取消收藏题库")
  @DeleteMapping("/{bankId}")
  public ApiResponse<Void> remove(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable Long bankId) {
    jdbc.update(
        "DELETE FROM user_question_bank_favorite WHERE user_id=? AND bank_id=?",
        userId(authorization),
        bankId);
    return ApiResponse.ok(null);
  }

  private long userId(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    try {
      return Long.parseLong(tokens.subject(authorization.substring(7), false));
    } catch (RuntimeException exception) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }
  }
}
