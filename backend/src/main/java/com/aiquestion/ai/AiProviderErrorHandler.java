package com.aiquestion.ai;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

final class AiProviderErrorHandler extends DefaultResponseErrorHandler {
    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String reason = switch (status) {
            case 401 -> "API Key 无效或已过期";
            case 403 -> "服务商拒绝访问，请检查密钥和模型权限";
            case 404 -> "模型或接口地址不存在";
            case 429 -> "调用限流或额度不足";
            case 408, 504 -> "模型服务响应超时";
            case 400, 422 -> "模型名称或请求格式不受支持";
            default -> "模型服务请求失败";
        };
        throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, reason + "（HTTP " + status + "）");
    }
}
