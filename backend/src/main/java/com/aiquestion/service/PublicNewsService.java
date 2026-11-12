package com.aiquestion.service;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import static com.aiquestion.controller.PublicNewsController.*;
import com.aiquestion.repository.PublicNewsRepository;

@org.springframework.stereotype.Service()
public class PublicNewsService {

    private final PublicNewsRepository persistence;

    public PublicNewsService(PublicNewsRepository persistence) {
        this.persistence = persistence;
    }

    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(persistence.findPublishedNews());
    }

    public ApiResponse<Map<String, Object>> get(Long id) {
        try {
            return ApiResponse.ok(com.aiquestion.repository.RepositoryRows.one(persistence.findPublishedNewsById(id)));
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在或已下线");
        }
    }
}
