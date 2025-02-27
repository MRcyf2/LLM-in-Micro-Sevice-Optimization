package org.example.llm.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.llm.entity.DyThreadPoolStatus;
import org.example.llm.entity.Param;
import org.example.llm.entity.ThreadPoolStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * DeepSeek 客户端，用于调用 LLM 分析线程池指标并返回参数建议
 */
@Component
public class DeepseekClient {

    //@Value
    private static final String apiKey="sk-7287e9b322294da998d78dac23273499";

    private static final String endpoint="https://api.deepseek.com/v1/chat/completions";

    private int timeoutSeconds=10;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Integer> analyzeThreadPool(ThreadPoolStatus status) throws Exception {
        // 1. 构建请求体
        String requestBody = buildRequestBody(status);

        // 2. 创建 HTTP 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 3. 发送请求并获取响应
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 4. 检查 HTTP 状态码
        if (response.statusCode() != 200) {
            throw new RuntimeException("DeepSeek API 请求失败: HTTP " + response.statusCode());
        }

        // 5. 解析响应
        String content = extractContentFromResponse(response.body());
        List<Param> adjustments = parseTableResponse(content);

        // 6. 提取参数并验证
        int newCorePoolSize = status.getCorePoolSize(); // 默认维持原值
        int newMaxPoolSize = status.getMaxPoolSize();

        for (Param param : adjustments) {
            switch (param.getName()) {
                case "核心线程数":
                    newCorePoolSize = parseValue(param.getNewValue());
                    break;
                case "最大线程数":
                    newMaxPoolSize = parseValue(param.getNewValue());
                    break;
            }
        }

        // 验证参数
        validateParams(newCorePoolSize, newMaxPoolSize);

        return Arrays.asList(newCorePoolSize, newMaxPoolSize);
    }


    private String extractContentFromResponse(String jsonResponse) throws Exception {
        // 解析JSON获取content内容
        Map<String, Object> responseMap = objectMapper.readValue(jsonResponse, Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("无效的API响应格式");
        }
        return (String) choices.get(0).get("message");
    }

    private List<Param> parseTableResponse(String content) {
        List<Param> params = new ArrayList<>();
        String[] lines = content.split("\n");

        for (String line : lines) {
            if (line.contains("|")) {
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    String name = parts[0].trim();
                    String newVal = parts[1].trim();
                    String delta = parts[2].trim();
                    if (name.startsWith("核心") || name.startsWith("最大")) {
                        params.add(new Param(name, newVal, delta));
                    }
                }
            }
        }
        return params;
    }

    private String buildRequestBody(ThreadPoolStatus status) throws JsonProcessingException, JsonProcessingException {
        String statusDescription = String.format(
                "核心线程数（corePoolSize）=%d\n" +
                        "最大线程数（maxPoolSize）=%d\n" +
                        "队列任务数（queueSize）=%d\n" +
                        "空闲线程存活时间（keepAliveTime）=%d秒\n" +
                        "拒绝策略（rejectedExecutionHandler）=%s\n" +
                        "队列类型（workQueue）=%s",
                status.getCorePoolSize(),
                status.getMaxPoolSize(),
                status.getQueueSize(),
                status.getKeepAliveTime(),
                status.getRejectedExecutionHandler(),
                status.getWorkQueue()
        );

        // 强制要求 AI 返回表格格式
        String prompt = String.format(
                "请分析以下线程池状态，并按严格的三行表格格式返回优化建议,建议内容只能涉及核心线程数、最大线程数，：\n\n" +
                        "### 当前状态\n%s\n\n" +
                        "### 返回格式要求\n" +
                        "参数名         | 变化后的参数 | 变化值\n" +
                        "------------|------------|-------\n" +
                        "核心线程数     | 6          | +2\n" +
                        "最大线程数     | 25         | +5\n" +
                        "\n" +
                        "注意：只需返回表格内容，不要包含额外文本。",
                statusDescription
        );

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", "deepseek-chat");
        requestMap.put("temperature", 0.7);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "user",
                "content", prompt
        ));
        requestMap.put("messages", messages);

        return objectMapper.writeValueAsString(requestMap);
    }

    // 参数校验逻辑
    private int parseValue(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的数值参数: " + value);
        }
    }
    private void validateParams(int corePoolSize, int maxPoolSize) {
        if (corePoolSize <= 0 || maxPoolSize <= 0 ) {
            throw new IllegalArgumentException("线程池参数无效");
        }
        if (corePoolSize > maxPoolSize) {
            throw new IllegalArgumentException("核心线程数不能大于最大线程数");
        }
    }

}
