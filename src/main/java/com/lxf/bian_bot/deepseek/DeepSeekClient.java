package com.lxf.bian_bot.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxf.bian_bot.config.model.DeepSeekConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final ObjectMapper objectMapper;
    private final DeepSeekConfig deepSeekConfig;

    public String chat(String systemPrompt, String userPrompt) {
        String apiKey = deepSeekConfig.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DeepSeek API Key");
        }

        DeepSeekChatRequest request = new DeepSeekChatRequest(
                deepSeekConfig.model(),
                List.of(
                        new DeepSeekMessage("system", systemPrompt),
                        new DeepSeekMessage("user", userPrompt)
                ),
                new DeepSeekResponseFormat("json_object"),
                false
        );

        /*
         * 项目中 Binance WebSocket 使用了 Jetty 11，而 Spring Boot 3.5 / Spring 6.2
         * 使用 RestClient 时可能使用 Spring 管理的 Jetty HTTP Client。
         * 如果 classpath 上同时存在不兼容的 Jetty 版本，会出现：
         *
         * NoSuchMethodError:
         * HttpClient.newRequest(java.net.URI)
         *
         * DeepSeek 是普通 HTTP 请求，这里明确使用 JDK/URLConnection 实现，
         * 不让 DeepSeek HTTP 请求依赖 Jetty，从根上避免 Jetty 版本冲突。
         */
        RestClient restClient = RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl(deepSeekConfig.baseUrl())
                .build();

        String response = restClient
                .post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("DeepSeek 返回为空");
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("DeepSeek 返回中没有 message.content: " + response);
            }
            return content.asText();
        } catch (Exception e) {
            throw new IllegalStateException("解析 DeepSeek 返回失败: " + response, e);
        }
    }

    private record DeepSeekChatRequest(
            String model,
            List<DeepSeekMessage> messages,
            DeepSeekResponseFormat response_format,
            boolean stream
    ) {}

    private record DeepSeekMessage(String role, String content) {}

    private record DeepSeekResponseFormat(String type) {}
}
