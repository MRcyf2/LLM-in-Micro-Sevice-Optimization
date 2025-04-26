package org.example.llm.core;

import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class ThreadPoolTuningScheduler {
    private final DeepseekClient deepseekClient;
    private final ThreadPoolRegistry registry;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4); // 独立调度线程池
    private final Executor asyncExecutor = Executors.newFixedThreadPool(4); // 异步执行器
    private final Logger log = org.slf4j.LoggerFactory.getLogger(ThreadPoolTuningScheduler.class);
    private final Map<String, ScheduledFuture<?>> tuningFutures = new ConcurrentHashMap<>();

    public ThreadPoolTuningScheduler(DeepseekClient deepseekClient, ThreadPoolRegistry registry) {
        this.deepseekClient = deepseekClient;
        this.registry = registry;
    }

    // 启动5秒间隔的异步调优（针对单个线程池）
    public void startAutoTuning(String poolName) {
        if (tuningFutures.containsKey(poolName)) return;

        // 立即执行一次，之后每5秒执行
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> asyncTune(poolName), 0, 5, TimeUnit.SECONDS
        );
        tuningFutures.put(poolName, future);
        log.info("启动线程池[{}]的异步调优（每5秒一次）", poolName);
    }

    // 核心异步调优逻辑
    @Async("tuningAsyncPool") // 自定义异步线程池（避免与调度线程共用）
    public void asyncTune(String poolName) {
        if (registry.getPoolDetail(poolName) == null) {
            log.warn("线程池[{}]未注册，跳过调优", poolName);
            return;
        }
        try {
            // 1. 获取Deepseek建议
            Map<String, Integer> suggestion = CompletableFuture.supplyAsync(
                    () -> deepseekClient.getRecommendationByPool(poolName),
                    asyncExecutor
            ).get(10, TimeUnit.SECONDS); // 10秒超时保护

            // 2. 校验建议合法性
            int newCore = Math.max(
                    registry.getMinCore(poolName),
                    Math.min(suggestion.get("core"), registry.getMaxCore(poolName))
            );
            int newMax = Math.max(newCore,
                    Math.min(suggestion.get("max"), registry.getMaxCore(poolName))
            );

            // 3. 调整线程池参数
            registry.adjust(poolName, newCore, newMax);
            log.info("线程池[{}]调优完成：core={}→{}，max={}→{}",
                    poolName, registry.getCurrentCore(poolName), newCore,
                    registry.getCurrentMax(poolName), newMax);

        } catch (TimeoutException e) {
            log.warn("Deepseek调用超时（pool={}），跳过本次调优", poolName, e);
        } catch (Exception e) {
            log.error("线程池[{}]调优失败：{}", poolName, e.getMessage(), e);
        }
    }

    // 停止调优
    public void stopAutoTuning(String poolName) {
        ScheduledFuture<?> future = tuningFutures.remove(poolName);
        if (future != null && !future.isDone()) {
            future.cancel(true); // 中断未完成的调优任务
        }
        log.info("停止线程池[{}]的异步调优", poolName);
    }

    public Serializable isTuningActive(String poolName) {
        ScheduledFuture<?> future = tuningFutures.get(poolName);
        if (future != null && !future.isDone()) {
            return true; // 调优任务正在进行
        }
        return false; // 调优任务未启动或已完成
    }
}