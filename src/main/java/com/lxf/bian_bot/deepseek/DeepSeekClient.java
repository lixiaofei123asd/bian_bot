package com.lxf.bian_bot.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxf.bian_bot.config.model.DeepSeekConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

        String response = RestClient.builder()
                .baseUrl(deepSeekConfig.baseUrl())
                .build()
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
