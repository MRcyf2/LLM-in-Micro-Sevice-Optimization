package org.example.llm.core;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.example.llm.entity.ThreadPoolDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ThreadPoolFileRecorder {
    private final ThreadPoolRegistry registry;
    private final ThreadPoolMetricsExporter metricsExporter;
    private final Path logDir = Paths.get("logs/threadpool");
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolFileRecorder.class);
    private static final int MAX_RECORDS = 60; // 最大保留记录数
    private static final String HEADER = "timestamp,config_min_core,config_max_core,config_max_queue," +
            "current_core,current_max,active_threads,queue_size,queue_capacity,queue_utilization,idle_ratio";

    @PostConstruct
    public void initDir() throws IOException {
        Files.createDirectories(logDir);
        logger.info("初始化线程池日志目录: {}", logDir);
    }

    @Scheduled(fixedDelay = 5_000) // 等待前次任务完成，避免积压
    public void recordToFiles() {
        registry.getAllPoolDetails().forEach(this::writePoolDetailWithRotation);
    }

    private void writePoolDetailWithRotation(ThreadPoolDetail detail) {
        Path poolFile = getPoolFilePath(detail.getPoolName());

        try {
            // 1. 读取现有内容（含表头）
            List<String> lines = Files.exists(poolFile) ?
                    Files.readAllLines(poolFile, StandardCharsets.UTF_8) :
                    new ArrayList<>();

            // 2. 处理表头（始终保留第一行）
            boolean hasHeader = !lines.isEmpty() && lines.get(0).equals(HEADER);
            List<String> dataLines = hasHeader ? lines.subList(1, lines.size()) : lines;

            // 3. 保留最新60条数据（含当前记录）
            List<String> newDataLines = new ArrayList<>(dataLines);
            newDataLines.add(buildCSVLine(detail));
            if (newDataLines.size() > MAX_RECORDS) {
                int removed = newDataLines.size() - MAX_RECORDS;
                newDataLines = newDataLines.subList(removed, newDataLines.size());
                logger.debug("截断文件[{}]，移除{}条旧记录", poolFile, removed);
            }

            // 4. 写入新内容（表头+数据）
            List<String> output = new ArrayList<>();
            if (hasHeader) {
                output.add(HEADER);
            }
            output.addAll(newDataLines);

            Files.write(poolFile, output, StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);

            logger.trace("写入线程池[{}]数据，当前记录数: {}", detail.getPoolName(), newDataLines.size());

        } catch (IOException e) {
            logger.error("文件[{}]写入失败: {}", poolFile, e.getMessage());
        }
    }

    // 避免重复字符串拼接
    static {
        CSVFormat.DEFAULT.withHeader(HEADER.split(",")).withDelimiter(',');
    }

    private Path getPoolFilePath(String poolName) {
        String safeName = sanitizeFileName(poolName);
        return logDir.resolve(safeName + "Detail.txt");
    }

    private String buildCSVLine(ThreadPoolDetail detail) {
        ThreadPoolExecutor executor = registry.getExecutor(detail.getPoolName());
        BlockingQueue<?> queue = executor.getQueue();

        // 复用MetricsExporter中的计算方法
        int queueCapacity = metricsExporter.getSafeQueueCapacity(queue);
        double queueUtilization = metricsExporter.calculateQueueUtilization(
                queue.size(),
                queueCapacity
        );
        double idleRatio = metricsExporter.calculateIdleRatio(
                executor.getPoolSize(),
                executor.getActiveCount()
        );
        return String.join(",",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                // 基础配置参数
                String.valueOf(detail.getConfigMinCore()),
                String.valueOf(detail.getConfigMaxCore()),
                String.valueOf(detail.getConfigMaxQueue()),
                // 运行时指标
                String.valueOf(detail.getCurrentCore()),
                String.valueOf(detail.getCurrentMax()),

                String.valueOf(executor.getActiveCount()),
                String.valueOf(queue.size()),
                // 扩展指标（与MetricsExporter保持一致）
                String.valueOf(queueCapacity),
                String.format("%.4f", queueUtilization), // 保留4位小数
                String.format("%.4f", idleRatio)
        );
    }

    private String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9_.-]", "_")
                .replaceAll("_+", "_") // 合并连续下划线
                .replaceAll("^_|_$", ""); // 移除首尾下划线
    }

    // 文件大小监控
    @Scheduled(fixedRate = 60_000)
    private void monitorFileSize() {
        try {
            Files.list(logDir)
                    .filter(p -> p.toString().endsWith("Detail.txt"))
                    .forEach(p -> {
                        long lineCount = countLines(p);
                        if (lineCount > MAX_RECORDS + 1) { // +1含表头
                            logger.warn("文件[{}]记录数异常: {}条（限制{}条）",
                                    p, lineCount - 1, MAX_RECORDS);
                        }
                    });
        } catch (IOException e) {
            logger.error("文件监控失败", e);
        }
    }

    private long countLines(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.count();
        } catch (IOException e) {
            return 0;
        }
    }
}