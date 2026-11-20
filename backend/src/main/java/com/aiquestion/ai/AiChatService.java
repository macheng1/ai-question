package com.aiquestion.ai;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiChatService {
    private final AiSettingsRepository settings;
    private final AiKeyCipher cipher;
    private final JdkClientHttpRequestFactory transport;

    AiChatService(AiSettingsRepository settings, AiKeyCipher cipher) {
        this.settings = settings;
        this.cipher = cipher;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        transport = new JdkClientHttpRequestFactory(http);
        transport.setReadTimeout(Duration.ofSeconds(60));
    }

    /** Callers supply the administrator ID from their authenticated business context. */
    public String chat(long owner, String instructions, String input) {
        return chat(owner, instructions, List.of(new UserMessage(input)));
    }

    String chat(long owner, String instructions, List<Message> messages) {
        return chat(owner, connection(owner), instructions, messages);
    }

    public reactor.core.publisher.Flux<String> stream(long owner, String instructions, List<Message> messages) {
        return clientFor(owner, connection(owner)).prompt().system(instructions).messages(messages)
                .stream().content().timeout(Duration.ofSeconds(60));
    }

    public record ConnectionResult(String model, long elapsedMs) {}

    public ConnectionResult testConnection(long owner) {
        var config = connection(owner);
        long start = System.nanoTime();
        chat(owner, config, "请简短回答。", List.of(new UserMessage("只回复 OK")));
        return new ConnectionResult(config.model(), Duration.ofNanos(System.nanoTime() - start).toMillis());
    }

    private AiSettingsRepository.Connection connection(long owner) {
        var config = settings.connection(owner);
        if (config == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先在系统管理中保存 AI 配置");
        }
        return config;
    }

    private String chat(long owner, AiSettingsRepository.Connection config, String instructions, List<Message> messages) {
        try {
            var answer = clientFor(owner, config).prompt().system(instructions).messages(messages).call().content();
            if (answer == null || answer.isBlank()) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "模型服务返回了空内容");
            }
            return answer;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException) {
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "连接或等待模型响应超时");
                }
            }
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "模型服务调用失败，请检查网络、接口地址和响应格式");
        }
    }

    private ChatClient clientFor(long owner, AiSettingsRepository.Connection config) {
        var api = OpenAiApi.builder()
            .baseUrl(config.baseUrl())
            .completionsPath("/chat/completions")
            .apiKey(cipher.decrypt(owner, config.encryptedApiKey()))
            .restClientBuilder(RestClient.builder().requestFactory(transport))
            .responseErrorHandler(new AiProviderErrorHandler())
            .build();
        var model = OpenAiChatModel.builder().openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(config.model()).build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
        return ChatClient.create(model);
    }
}
