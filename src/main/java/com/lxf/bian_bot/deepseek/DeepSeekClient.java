package com.lxf.bian_bot.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private static final String BASE_URL = "https://api.deepseek.com";
    private static final String MODEL = "deepseek-v4-pro";

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().baseUrl(BASE_URL).build();

    public String chat(String systemPrompt, String userPrompt) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置环境变量 DEEPSEEK_API_KEY");
        }

        DeepSeekChatRequest request = new DeepSeekChatRequest(
                MODEL,
                List.of(
                        new DeepSeekMessage("system", systemPrompt),
                        new DeepSeekMessage("user", userPrompt)
                ),
                new DeepSeekResponseFormat("json_object"),
                false
        );

        String response = restClient.post()
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
