package com.aiquestion.service;

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
import static com.aiquestion.controller.ProfileRecordController.*;
import com.aiquestion.repository.ProfileRecordRepository;

/**
 * 当前登录人的考试记录与错题查询接口。正式记录只读数据库，不依赖浏览器缓存。
 */
@org.springframework.stereotype.Service()
public class ProfileRecordService {

    private final ProfileRecordRepository persistence;

    private final JwtTokenService tokenService;

    public ProfileRecordService(ProfileRecordRepository persistence, JwtTokenService tokenService) {
        this.persistence = persistence;
        this.tokenService = tokenService;
    }

    public ApiResponse<List<Map<String, Object>>> records(String authorization) {
        Identity identity = identity(authorization);
        return ApiResponse.ok(persistence.findRecords(identity.userId(), identity.phone()));
    }

    public ApiResponse<List<Map<String, Object>>> mistakes(String authorization) {
        Identity identity = identity(authorization);
        List<Map<String, Object>> rows = persistence.findLatestMistakes(identity.userId(), identity.phone(), identity.userId(), identity.phone());
        rows.forEach(row -> row.put("options", options(row.get("questionId"))));
        return ApiResponse.ok(rows);
    }

    private List<Map<String, Object>> options(Object questionId) {
        if (questionId == null) {
            return List.of();
        }
        return persistence.findQuestionOptions(com.aiquestion.repository.RepositoryRows.nullableLong(questionId));
    }

    private record Identity(Long userId, String phone) {
    }

    private Identity identity(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            Long userId = Long.valueOf(tokenService.subject(authorization.substring(7), false));
            List<String> phones = persistence.findUserPhone(userId).stream().map(rs -> com.aiquestion.repository.RepositoryRows.string(rs, "phone")).toList();
            if (phones.isEmpty()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return new Identity(userId, phones.get(0));
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
