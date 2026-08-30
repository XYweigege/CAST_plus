package com.voc.insight.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenRouter AI 客户端。
 * 使用 Spring 6.1+ 的 RestClient（同步 HTTP 客户端，位于 spring-web 中，
 * 无需额外引入 WebFlux）。
 */
@Slf4j
@Component
public class AiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public AiClient(
            @Value("${voc.openrouter.base-url}") String baseUrl,
            @Value("${voc.openrouter.api-key}") String apiKey,
            @Value("${voc.openrouter.model}") String model,
            RestClient.Builder builder) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * 判断 AI 服务是否真的可用。
     * 只判断非空不够：配置文件里的占位 Key 也是非空，
     * 会导致每次都发出必然失败的请求，再走兜底，白白增加延迟与错误日志。
     */
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && !apiKey.startsWith("your_");
    }

    public String getModel() {
        return model;
    }

    /**
     * 调用 OpenRouter Chat Completions 接口。
     *
     * @param systemPrompt 系统提示词
     * @param userContent  用户输入
     * @param temperature  温度（标注任务建议 0.1，保持输出一致性）
     * @param maxTokens    最大输出 token
     * @return 模型输出的文本内容
     */
    public String chat(String systemPrompt, String userContent, double temperature, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)
                ),
                "temperature", temperature,
                "max_tokens", maxTokens
        );

        String response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("AI 响应缺少 choices: " + response);
            }
            return choices.get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("解析 AI 响应失败: " + e.getMessage(), e);
        }
    }
}
