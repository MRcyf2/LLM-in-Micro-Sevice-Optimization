//package org.example.llm.core;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.apache.commons.csv.CSVFormat;
//import org.apache.commons.csv.CSVParser;
//import org.apache.commons.csv.CSVRecord;
//import org.apache.http.client.config.RequestConfig;
//import org.apache.http.client.methods.CloseableHttpResponse;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.entity.StringEntity;
//import org.apache.http.impl.client.CloseableHttpClient;
//import org.apache.http.impl.client.HttpClients;
//import org.apache.http.util.EntityUtils;
//import java.util.concurrent.Executor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Component;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.*;
//
//@Component("Ollama")
//public class OllamaClient {
//    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";
//    private static final Path LOG_DIR = Paths.get("logs/threadpool");
//    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);
//
//    private final Executor asyncExecutor;
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    public OllamaClient(@Qualifier("tuningAsyncPool") Executor asyncExecutor) {
//        this.asyncExecutor = asyncExecutor;
//    }
//
//    public Map<String, Integer> getRecommendationByPool(String poolName) {
//        try {
//            Path poolFile = buildPoolFilePath(poolName);
//            validateLogFile(poolFile);
//            List<Map<String, Object>> dataPoints = parseDataFile(poolFile);
//            if (dataPoints.isEmpty()) {
//                throw new IllegalArgumentException("数据文件为空或格式不正确: " + poolFile);
//            }
//
//            String requestBody = buildOllamaRequest(poolName, dataPoints);
//            return callOllamaApi(requestBody);
//        } catch (Exception e) {
//            logger.error("获取线程池[{}]建议失败", poolName, e);
//            throw new RuntimeException("建议获取失败: " + e.getMessage(), e);
//        }
//    }
//
//    private String buildOllamaRequest(String poolName, List<Map<String, Object>> fullData)
//            throws JsonProcessingException {
//        int configMinCore = ((Number) fullData.get(0).get("config_min_core")).intValue();
//        int configMaxCore = ((Number) fullData.get(0).get("config_max_core")).intValue();
//        int configMaxQueue = ((Number) fullData.get(0).get("config_max_queue")).intValue();
//
//        // 完全保持原有提示词结构
//        String systemPrompt = String.format(
//                """
//                【强制格式要求】
//                你是一个线程池优化专家，请分析以下数据。
//                当前线程池配置限制：
//                - 配置最小核心线程数为：%d
//                - 配置最大线程数为：%d
//                - 最大队列容量：%d
//
//                数据的格式如下:
//                - timestamp: 时间戳
//                - config_min_core: 配置最小核心线程数
//                - config_max_core: 配置最大线程数
//                - config_max_queue: 配置队列上限
//                - current_core: 当前核心线程数
//                - current_max: 当前最大线程数
//                - active_threads: 活跃线程数
//                - queue_size: 队列当前大小
//                - queue_capacity: 队列当前容量
//                - queue_utilization: 队列使用率
//                - idle_ratio: 线程空闲率
//
//                你需要分析这些数据并给出建议的核心线程数和最大线程数。
//                配置最小核心线程数和配置最大线程数是固定的
//                你的建议中核心线程数必须比最小核心线程数大，最大线程数必须小于等于配置最大线程数
//                当队列使用率超过80%%时，建议增加核心线程数和最大线程数；当队列使用率低于50%%时，建议减少核心线程数和最大线程数
//
//                【输出要求】
//                1. 必须且只能返回JSON格式
//                2. 包含两个整数字段：core 和 max，表示建议的核心线程数和最大线程数
//                3. max必须大于等于core
//
//                【示例】
//                {"core":8,"max":16}
//
//                完整监控数据（JSON数组）：
//                %s
//
//                请直接返回优化建议：""",
//                configMinCore, configMaxCore, configMaxQueue,
//                objectMapper.writeValueAsString(fullData)  // 使用完整数据
//        );
//
//        Map<String, Object> request = new LinkedHashMap<>();
//        request.put("model", "deepseek-r1");
//        request.put("prompt", systemPrompt);
//        request.put("stream", false);
//        request.put("format", "json");
//
//        return objectMapper.writeValueAsString(request);
//    }
//
//    private Map<String, Integer> callOllamaApi(String requestBody) throws Exception {
//        try (CloseableHttpClient httpClient = createHttpClient()) {
//            HttpPost httpPost = new HttpPost(OLLAMA_API_URL);
//            httpPost.setHeader("Content-Type", "application/json");
//            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
//
//            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
//                validateResponse(response);
//                return parseOllamaResponse(response);
//            }
//        }
//    }
//
//    private Map<String, Integer> parseOllamaResponse(CloseableHttpResponse response) throws IOException {
//        String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
//        JsonNode root = objectMapper.readTree(responseBody);
//
//        if (root.has("error")) {
//            throw new RuntimeException("Ollama服务错误: " + root.get("error").asText());
//        }
//
//        JsonNode responseNode = objectMapper.readTree(root.get("response").asText());
//        return Map.of(
//                "core", responseNode.get("core").asInt(),
//                "max", responseNode.get("max").asInt()
//        );
//    }
//
//    // 以下辅助方法保持不变
//    private Path buildPoolFilePath(String poolName) {
//        String safeName = poolName.replaceAll("[^a-zA-Z0-9_-]", "_");
//        return LOG_DIR.resolve(safeName + "Detail.txt");
//    }
//
//    private void validateLogFile(Path poolFile) {
//        if (!Files.exists(poolFile)) {
//            throw new IllegalArgumentException("日志文件不存在: " + poolFile);
//        }
//        if (Files.isDirectory(poolFile)) {
//            throw new IllegalArgumentException("路径指向目录而非文件: " + poolFile);
//        }
//    }
//
//    private List<Map<String, Object>> parseDataFile(Path poolFile) throws IOException {
//        List<Map<String, Object>> data = new ArrayList<>();
//        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
//                .setHeader("timestamp", "config_min_core", "config_max_core",
//                        "config_max_queue", "current_core", "current_max",
//                        "active_threads", "queue_size", "queue_capacity",
//                        "queue_utilization", "idle_ratio")
//                .setSkipHeaderRecord(true)
//                .build();
//
//        try (BufferedReader reader = Files.newBufferedReader(poolFile);
//             CSVParser csvParser = new CSVParser(reader, csvFormat)) {
//
//            for (CSVRecord record : csvParser) {
//                parseCSVRecord(record).ifPresent(data::add);
//            }
//        }
//        return data;
//    }
//
//    private Optional<Map<String, Object>> parseCSVRecord(CSVRecord record) {
//        try {
//            Map<String, Object> point = new LinkedHashMap<>();
//            point.put("timestamp", record.get("timestamp"));
//            point.put("config_min_core", parseInteger(record, "config_min_core"));
//            point.put("config_max_core", parseInteger(record, "config_max_core"));
//            point.put("config_max_queue", parseInteger(record, "config_max_queue"));
//            point.put("current_core", parseInteger(record, "current_core"));
//            point.put("current_max", parseInteger(record, "current_max"));
//            point.put("active_threads", parseInteger(record, "active_threads"));
//            point.put("queue_size", parseInteger(record, "queue_size"));
//            point.put("queue_capacity", parseInteger(record, "queue_capacity"));
//            point.put("queue_utilization", parseDouble(record, "queue_utilization"));
//            point.put("idle_ratio", parseDouble(record, "idle_ratio"));
//            return Optional.of(point);
//        } catch (Exception e) {
//            logger.warn("解析CSV记录失败，行号: {}，原因: {}", record.getRecordNumber(), e.getMessage());
//            return Optional.empty();
//        }
//    }
//
//    private int parseInteger(CSVRecord record, String column) {
//        String value = record.get(column);
//        try {
//            return Integer.parseInt(value.trim());
//        } catch (NumberFormatException e) {
//            throw new IllegalArgumentException(
//                    String.format("列[%s]值无效: '%s' (行号:%d)", column, value, record.getRecordNumber())
//            );
//        }
//    }
//
//    private double parseDouble(CSVRecord record, String column) {
//        String value = record.get(column);
//        try {
//            return Double.parseDouble(value.trim());
//        } catch (NumberFormatException e) {
//            throw new IllegalArgumentException(
//                    String.format("列[%s]值无效: '%s' (行号:%d)", column, value, record.getRecordNumber())
//            );
//        }
//    }
//
//    private CloseableHttpClient createHttpClient() {
//        return HttpClients.custom()
//                .setDefaultRequestConfig(RequestConfig.custom()
//                        .setConnectTimeout(30_000)
//                        .setSocketTimeout(60_000)
//                        .build())
//                .build();
//    }
//
//    private void validateResponse(CloseableHttpResponse response) throws IOException {
//        int statusCode = response.getStatusLine().getStatusCode();
//        if (statusCode < 200 || statusCode >= 300) {
//            String errorBody = EntityUtils.toString(response.getEntity());
//            logger.error("API请求失败: {} - {}", statusCode, errorBody);
//            throw new RuntimeException("API Error " + statusCode + ": " + errorBody);
//        }
//    }
//}
//
//
