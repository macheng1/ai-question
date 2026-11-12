package com.aiquestion.controller;

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

@RestController
@RequestMapping("/public/news")
public class /**
 * 后端业务组件。
 *
 * @author aiquestion
 */
PublicNewsController {

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return service.get(id);
    }

    private final com.aiquestion.service.PublicNewsService service;

    public /**
     * 后端业务组件。
     *
     * @author aiquestion
     */
    PublicNewsController(com.aiquestion.service.PublicNewsService service) {
        this.service = service;
    }
}
