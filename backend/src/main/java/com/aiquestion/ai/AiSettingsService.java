package com.aiquestion.ai;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;

@Service
public class AiSettingsService {
    private final AiSettingsRepository db;
    private final AiKeyCipher cipher;

    AiSettingsService(AiSettingsRepository db, AiKeyCipher cipher) {
        this.db = db;
        this.cipher = cipher;
    }

    public record Settings(String provider, String baseUrl, String model, boolean keyConfigured) {}
    public record Update(@NotBlank @Pattern(regexp = "bailian|deepseek|zhipu|moonshot|volcengine|siliconflow|baidu|minimax|hunyuan|xfyun|openai|gemini|custom") String provider,
                         @NotBlank @Size(max = 512) String baseUrl,
                         @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}") String model,
                         @Size(max = 768) String apiKey) {}

    public Settings read(long owner) {
        var config = db.configuration(owner);
        return config == null
            ? new Settings("bailian", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", false)
            : new Settings(config.provider(), config.baseUrl(), config.model(), true);
    }

    public Settings save(long owner, Update request) {
        String url = validatedUrl(request.baseUrl());
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            if (db.updateModel(owner, request.provider(), url, request.model()) == 0) {
                var current = db.configuration(owner);
                if (current == null || !current.provider().equals(request.provider()) || !current.baseUrl().equals(url)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "首次配置或更换服务商、接口地址时请填写对应的 API Key");
                }
            }
        } else {
            byte[] encrypted = cipher.encrypt(owner, request.apiKey().strip());
            db.save(owner, request.provider(), url, request.model(), encrypted);
        }
        return new Settings(request.provider(), url, request.model(), true);
    }

    private String validatedUrl(String value) {
        try {
            var uri = java.net.URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && value.chars().allMatch(ch -> ch < 128)) {
                return value.replaceAll("/+$", "");
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid URI syntax uses the same validation response.
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写有效的 HTTPS 接口地址");
    }

}
