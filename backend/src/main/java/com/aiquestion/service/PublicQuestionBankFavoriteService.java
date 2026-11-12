package com.aiquestion.service;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import static com.aiquestion.controller.PublicQuestionBankFavoriteController.*;
import com.aiquestion.repository.PublicQuestionBankFavoriteRepository;

/**
 * 登录用户的公开题库收藏接口。收藏状态以数据库为准，避免只保存在浏览器导致跨设备丢失。
 */
@org.springframework.stereotype.Service()
public class PublicQuestionBankFavoriteService {

    private final PublicQuestionBankFavoriteRepository persistence;

    private final JwtTokenService tokens;

    public PublicQuestionBankFavoriteService(PublicQuestionBankFavoriteRepository persistence, JwtTokenService tokens) {
        this.persistence = persistence;
        this.tokens = tokens;
    }

    public ApiResponse<List<Long>> list(String authorization) {
        long userId = userId(authorization);
        return ApiResponse.ok(persistence.findFavoriteBankIds(userId));
    }

    public ApiResponse<Void> add(String authorization, Long bankId) {
        long userId = userId(authorization);
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countPublishedBank(bankId)) == 0) {
            throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
        }
        persistence.insertFavorite(userId, bankId);
        return ApiResponse.ok(null);
    }

    public ApiResponse<Void> remove(String authorization, Long bankId) {
        persistence.deleteFavorite(userId(authorization), bankId);
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
