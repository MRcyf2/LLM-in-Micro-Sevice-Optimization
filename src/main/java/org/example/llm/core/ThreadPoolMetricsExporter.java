package org.example.llm.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.example.llm.entity.ResizableLinkedBlockingQueue;
import org.example.llm.entity.ThreadPoolDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.function.ToDoubleFunction;


@Component
@RequiredArgsConstructor
public class ThreadPoolMetricsExporter {
    private final ThreadPoolRegistry registry;
    private final MeterRegistry meterRegistry;
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolMetricsExporter.class);

    // 线程安全的注册池跟踪
    private final Set<String> registeredPools = ConcurrentHashMap.newKeySet();

    // 初始化指标注册（延迟500ms等待其他组件）
    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        try {
            Thread.sleep(500);
            registry.getAllPoolDetails().forEach(this::safeRegisterMetrics);
            logger.info("初始化指标. Total pools: {}", registeredPools.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Metrics registration interrupted", e);
        }
    }

    // 动态刷新机制（每30秒）
    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
//        logger.info("【定时任务触发】开始刷新指标...");
        registry.getAllPoolDetails().stream()
                .filter(detail -> !registeredPools.contains(detail.getPoolName()))
                .forEach(this::safeRegisterMetrics);
//        logger.info("【定时任务完成】已跟踪池数: {}", registeredPools.size());
    }

    // 安全注册指标（含异常隔离）
    private void safeRegisterMetrics(ThreadPoolDetail detail) {
        String poolName = detail.getPoolName();
        try {
            if (registeredPools.add(poolName)) {
                registerMetrics(detail);
//                logger.info("Successfully registered metrics for pool: {}", poolName);
            }
        } catch (Exception e) {
            registeredPools.remove(poolName); // 允许重试
            logger.error("Failed to register metrics for pool: {}", poolName, e);
        }
    }

    // 核心指标注册逻辑
    private void registerMetrics(ThreadPoolDetail detail) {
        String poolName = detail.getPoolName();
        ThreadPoolExecutor executor = registry.getExecutor(poolName);
        BlockingQueue<?> queue = executor.getQueue();

        // 核心线程指标
        /*
         * 1. core.size: 核心线程数
         * 2. active.threads: 活跃线程数
         * 3. max.size: 最大线程数
         */
        registerGauge("core.size", executor, ThreadPoolExecutor::getCorePoolSize, poolName);
        registerGauge("active.threads", executor, ThreadPoolExecutor::getActiveCount, poolName);
        registerGauge("max.size", executor, ThreadPoolExecutor::getMaximumPoolSize, poolName);

        // 队列指标
        registerQueueMetrics(queue, poolName);

        // 线程利用率指标
        registerGauge("idle.ratio", executor,
                e -> calculateIdleRatio(e.getPoolSize(), e.getActiveCount()),
                poolName
        );
    }

    // 通用Gauge注册方法
    private <T> void registerGauge(String metricName, T obj, ToDoubleFunction<T> func, String poolName) {
        Gauge.builder("threadpool." + metricName, obj, func)
                .tag("pool", poolName)
                .register(meterRegistry);
    }

    // 队列指标注册
    private void registerQueueMetrics(BlockingQueue<?> queue, String poolName) {
        // 队列指标
        /**
         * queue.size: 当前队列大小
         * queue.capacity: 队列容量
         * queue.utilization: 队列使用率
         */
        registerGauge("queue.size", queue, BlockingQueue::size, poolName);
        registerGauge("queue.capacity", queue, this::getSafeQueueCapacity, poolName);
        registerGauge("queue.utilization", queue,
                q -> calculateQueueUtilization(q.size(), getSafeQueueCapacity(q)),
                poolName
        );
    }

    // 安全获取队列容量
    int getSafeQueueCapacity(BlockingQueue<?> queue) {
        try {
            if (queue instanceof ResizableLinkedBlockingQueue) {
                return (int) ((ResizableLinkedBlockingQueue<?>) queue).capacity();
            } else if (queue instanceof LinkedBlockingQueue) {
                return ((LinkedBlockingQueue<?>) queue).remainingCapacity() + queue.size();
            }
            return queue.remainingCapacity() + queue.size(); // 通用fallback
        } catch (Exception e) {
            logger.warn("Failed to get queue capacity for type: {}", queue.getClass().getSimpleName());
            return Integer.MAX_VALUE;
        }
    }

    // 计算队列使用率
    double calculateQueueUtilization(int currentSize, int capacity) {
        if (capacity <= 0) return 0.0;
        return Math.min(1.0, (double) currentSize / capacity);
    }

    // 计算空闲率
    double calculateIdleRatio(int poolSize, int activeCount) {
        if (poolSize <= 0) return 0.0;
        return (double) (poolSize - activeCount) / poolSize;
    }

    // 自动标签拒绝策略（与线程池配置强绑定）
    public static class InstrumentedRejectionHandler extends AbortPolicy {
        private final Counter counter;

        public InstrumentedRejectionHandler(String poolName, MeterRegistry registry) {
            this.counter = Counter.builder("threadpool.rejected.tasks")
                    .tag("pool", poolName)
                    .description("Total rejected tasks count")
                    .register(registry);
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            counter.increment();
            logger.warn("[Rejection] Pool: {} - Active: {}/{} Queue: {}/{}",
                    executor,
                    executor.getActiveCount(), executor.getMaximumPoolSize(),
                    executor.getQueue().size(), getQueueCapacity(executor.getQueue())
            );
            super.rejectedExecution(r, executor);
        }

        private int getQueueCapacity(BlockingQueue<?> queue) {
            if (queue instanceof ResizableLinkedBlockingQueue) {
                return (int) ((ResizableLinkedBlockingQueue<?>) queue).capacity();
            }
            return queue.size() + queue.remainingCapacity();
        }
    }
}