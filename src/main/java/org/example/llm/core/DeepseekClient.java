package org.example.llm.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * DeepSeek 客户端，用于调用 LLM 分析线程池指标并返回参数建议
 */
@Component("DeepSeek")
public class DeepseekClient {
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final Path LOG_DIR = Paths.get("logs/threadpool");
    private static final Logger logger = LoggerFactory.getLogger(DeepseekClient.class);

    private final Executor asyncExecutor;
    private String apiKey="sk-d642f05dc6ba4dcdb021b2314a2f99c2";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepseekClient(@Qualifier("tuningAsyncPool") Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

//    private void addUserMessage(List<Map<String, Object>> messages, List<Map<String, Object>> dataPoints)
//            throws JsonProcessingException {
//        String userContent = String.format("最新的数据：%s，请根据数据的变化验证你的判断，并返回下一次调优的建议",
//                objectMapper.writeValueAsString(dataPoints));
//        messages.add(Map.of(
//                "role", "user",
//                "content", userContent
//        ));
//    }

    public Map<String, Integer> getRecommendationByPool(String poolName) {
        try {
            Path poolFile = buildPoolFilePath(poolName);
            validateLogFile(poolFile);
            List<Map<String, Object>> dataPoints = parseDataFile(poolFile);
            if (dataPoints.isEmpty()) {
                throw new IllegalArgumentException("数据文件为空或格式不正确: " + poolFile);
            }

            String requestBody = buildSinglePoolRequest(poolName, dataPoints);
            return callDeepseekApi(requestBody);
        } catch (Exception e) {
            logger.error("获取线程池[{}]建议失败", poolName, e);
            throw new RuntimeException("建议获取失败: " + e.getMessage(), e);
        }
    }


    // 文件路径
    private Path buildPoolFilePath(String poolName) {
        String safeName = poolName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return LOG_DIR.resolve(safeName + "Detail.txt");//csv
    }

    // 文件验证
    private void validateLogFile(Path poolFile) {
        if (!Files.exists(poolFile)) {
            throw new IllegalArgumentException("日志文件不存在: " + poolFile);
        }
        if (Files.isDirectory(poolFile)) {
            throw new IllegalArgumentException("路径指向目录而非文件: " + poolFile);
        }
    }

    // region 请求构建
    private String buildSinglePoolRequest(String poolName, List<Map<String, Object>> fullData)
            throws JsonProcessingException {

        Map<String, Object> request = new LinkedHashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        int configMinCore = ((Number) fullData.get(0).get("config_min_core")).intValue();
        int configMaxCore = ((Number) fullData.get(0).get("config_max_core")).intValue();
        int configMaxQueue = ((Number) fullData.get(0).get("config_max_queue")).intValue();
        // 强化版系统提示
        String systemPrompt = String.format(
        """
        【强制格式要求】
        你是一个线程池优化专家，请分析以下数据。
        当前线程池配置限制：
        - 配置最小核心线程数为：%d
        - 配置最大线程数为：%d
        - 最大队列容量：%d
        
        数据的格式如下:
        - timestamp: 时间戳
        - config_min_core: 配置最小核心线程数
        - config_max_core: 配置最大线程数
        - config_max_queue: 配置队列上限
        - current_core: 当前核心线程数
        - current_max: 当前最大线程数
        - active_threads: 活跃线程数
        - queue_size: 队列当前大小
        - queue_capacity: 队列当前容量
        - queue_utilization: 队列使用率
        - idle_ratio: 线程空闲率
        
        你需要分析这些数据并给出建议的核心线程数和最大线程数。
        配置最小核心线程数和配置最大线程数是固定的
        你的建议中核心线程数必须比最小核心线程数大，最大线程数必须小于等于配置最大线程数
        当队列使用率超过80%%时，建议增加核心线程数和最大线程数；当队列使用率低于50%%时，建议减少核心线程数和最大线程数
        
        【输出要求】
        1. 必须且只能返回JSON格式
        2. 包含两个整数字段：core 和 max，表示建议的核心线程数和最大线程数
        3. max必须大于等于core
        
        【示例】
        {"core":8,"max":16}
        
        请直接返回优化建议：""",
                configMinCore, configMaxCore, configMaxQueue
        );

        messages.add(Map.of(
                "role", "system",
                "content", systemPrompt
        ));

        // 用户消息带解码说明
        messages.add(Map.of(
                "role", "user",
                "content", String.format("""
               完整监控数据（JSON数组）：
                %s""",
                        objectMapper.writeValueAsString(fullData)
                )
        ));

        request.put("messages", messages);
        request.put("model", "deepseek-chat");
        request.put("response_format", Map.of("type", "json_object"));

        return objectMapper.writeValueAsString(request);
    }


    private List<Map<String, Object>> parseDataFile(Path poolFile) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader("timestamp", "config_min_core", "config_max_core",
                        "config_max_queue", "current_core", "current_max",
                        "active_threads", "queue_size", "queue_capacity",
                        "queue_utilization", "idle_ratio")
                .setSkipHeaderRecord(true)
                .build();

        try (BufferedReader reader = Files.newBufferedReader(poolFile);
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            for (CSVRecord record : csvParser) {
                parseCSVRecord(record).ifPresent(data::add);
            }
        }
//        logger.info("解析文件成功: {}，记录数: {}", poolFile, data.size());
        return data;
    }

    private Optional<Map<String, Object>> parseCSVRecord(CSVRecord record) {
        try {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", record.get("timestamp"));
            point.put("config_min_core", parseInteger(record, "config_min_core"));
            point.put("config_max_core", parseInteger(record, "config_max_core"));
            point.put("config_max_queue", parseInteger(record, "config_max_queue"));
            point.put("current_core", parseInteger(record, "current_core"));
            point.put("current_max", parseInteger(record, "current_max"));
            point.put("active_threads", parseInteger(record, "active_threads"));
            point.put("queue_size", parseInteger(record, "queue_size"));

            // 新增字段
            point.put("queue_capacity", parseInteger(record, "queue_capacity"));
            point.put("queue_utilization", parseDouble(record, "queue_utilization"));
            point.put("idle_ratio", parseDouble(record, "idle_ratio"));

            return Optional.of(point);
        } catch (Exception e) {
            logger.warn("解析CSV记录失败，行号: {}，原因: {}", record.getRecordNumber(), e.getMessage());
            return Optional.empty();
        }
    }

    private int parseInteger(CSVRecord record, String column) {
        String value = record.get(column);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("列[%s]值无效: '%s' (行号:%d)", column, value, record.getRecordNumber())
            );
        }
    }
    private double parseDouble(CSVRecord record, String column) {
        String value = record.get(column);
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("列[%s]值无效: '%s' (行号:%d)", column, value, record.getRecordNumber())
            );
        }
    }

    // API调用
    private Map<String, Integer> callDeepseekApi(String requestBody) throws Exception {
        try (CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost httpPost = buildHttpRequest(requestBody);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                validateResponse(response);
                return parseApiResponse(response);
            }
        }
    }

    // 异步
    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(30_000)
                        .setSocketTimeout(60_000)
                        .build())
                .build();
    }

    private HttpPost buildHttpRequest(String requestBody) {
        HttpPost httpPost = new HttpPost(DEEPSEEK_API_URL);
        httpPost.setHeader("Authorization", "Bearer " + apiKey);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
        return httpPost;
    }

    private void validateResponse(CloseableHttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < 200 || statusCode >= 300) {
            String errorBody = EntityUtils.toString(response.getEntity());
            logger.error("API请求失败: {} - {}", statusCode, errorBody);
            throw new RuntimeException("API Error " + statusCode + ": " + errorBody);
        }
    }

    private Map<String, Integer> parseApiResponse(CloseableHttpResponse response) throws IOException {
        String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

        JsonNode root = objectMapper.readTree(responseBody);

        // 1. 校验核心结构
        if (!root.has("choices") || root.get("choices").isEmpty()) {
            throw new RuntimeException("响应缺少choices字段");
        }

        JsonNode firstChoice = root.get("choices").get(0);
        if (!firstChoice.has("message")) {
            throw new RuntimeException("choices[0]缺少message字段");
        }

        JsonNode messageNode = firstChoice.get("message");
        if (!messageNode.has("content")) {
            throw new RuntimeException("message节点缺少content字段");
        }

        // 2. 获取content字符串
        String contentJson = messageNode.get("content").asText();
        System.out.println("待解析的content内容:\n" + contentJson);

        // 3. 二次解析content中的JSON
        JsonNode contentNode = objectMapper.readTree(contentJson);
        if (!contentNode.has("core") || !contentNode.has("max")) {
            throw new RuntimeException("content缺少必要字段");
        }

        return Map.of(
                "core", contentNode.get("core").asInt(),
                "max", contentNode.get("max").asInt()
        );
    }

}