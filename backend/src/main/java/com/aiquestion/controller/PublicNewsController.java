package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/public/news")
public class PublicNewsController {
    private final JdbcTemplate jdbc;

    public PublicNewsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(jdbc.queryForList("""
                SELECT id, title, summary, content, published_at AS publishedAt
                FROM site_news
                WHERE status = 'PUBLISHED' AND published_at <= CURRENT_TIMESTAMP(3)
                ORDER BY published_at DESC, id DESC
                """));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        try {
            return ApiResponse.ok(jdbc.queryForMap("""
                    SELECT id, title, summary, content, published_at AS publishedAt
                    FROM site_news
                    WHERE id = ? AND status = 'PUBLISHED' AND published_at <= CURRENT_TIMESTAMP(3)
                    """, id));
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在或已下线");
        }
    }
}
