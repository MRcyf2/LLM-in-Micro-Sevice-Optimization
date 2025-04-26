package org.example.llm.core;

import org.example.llm.entity.ResizableLinkedBlockingQueue;
import org.example.llm.entity.ThreadPoolDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Component
public class ThreadPoolRegistry {
    private final Map<String, PoolMetadata> registry = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolRegistry.class);
    private final DeepseekClient deepseekClient;

    public ThreadPoolRegistry(DeepseekClient deepseekClient) {
        this.deepseekClient = deepseekClient;
    }

    public void register(String poolName, ThreadPoolExecutor executor,
                         int minCore, int maxCore, int maxQueue) {
        logger.info("注册线程池: {}, 参数: minCore={}, maxCore={}, maxQueue={}",
                poolName, minCore, maxCore, maxQueue);
        registry.put(poolName, new PoolMetadata(executor, minCore, maxCore, maxQueue));
    }

    public List<ThreadPoolDetail> getAllPoolDetails() {
        return registry.entrySet().stream()
                .map(entry -> {
                    PoolMetadata meta = entry.getValue();
                    ThreadPoolExecutor executor = meta.executor();
                    BlockingQueue<?> queue = executor.getQueue();

                    // 计算队列容量
                    int queueCapacity = getQueueCapacity(queue);

                    // 计算队列使用率（防除零）
                    double queueUtilization = queueCapacity > 0 ?
                            Math.min(1.0, (double) queue.size() / queueCapacity) : 0.0;

                    // 计算线程空闲率
                    int poolSize = executor.getPoolSize();
                    double idleRatio = poolSize > 0 ?
                            (double) (poolSize - executor.getActiveCount()) / poolSize : 0.0;

                    return new ThreadPoolDetail(
                            entry.getKey(),
                            meta.minCore(),
                            meta.maxCore(),
                            meta.maxQueueSize(),
                            executor.getCorePoolSize(),
                            executor.getMaximumPoolSize(),
                            executor.getActiveCount(),
                            queue.size(),
                            queueCapacity,
                            queueUtilization,
                            idleRatio
                    );
                })
                .collect(Collectors.toList());
    }

    private int getQueueCapacity(BlockingQueue<?> queue) {
        if (queue instanceof ResizableLinkedBlockingQueue) {
            return (int) ((ResizableLinkedBlockingQueue<?>) queue).capacity();
        } else {
            // 通用队列容量 = 剩余容量 + 已使用容量
            return queue.size() + queue.remainingCapacity();
        }
    }

    public synchronized void adjust(String poolName, int newCore, int newMax) {
        PoolMetadata meta = registry.get(poolName);
        int clampedCore = Math.max(meta.minCore, Math.min(newCore, meta.maxCore));
        int clampedMax = Math.max(clampedCore, Math.min(newMax, meta.maxCore));

        meta.executor.setCorePoolSize(clampedCore);
        meta.executor.setMaximumPoolSize(clampedMax);
    }

    public synchronized void adjustByAi(String poolName) {
        Map<String, Integer> recommendation = deepseekClient.getRecommendationByPool(poolName);
        int newCore = recommendation.get("core");
        int newMax = recommendation.get("max");
        PoolMetadata meta = registry.get(poolName);
        meta.executor.setCorePoolSize(newCore);
        meta.executor.setMaximumPoolSize(newMax);
    }

    public ThreadPoolExecutor getExecutor(String poolName) {
        PoolMetadata meta = registry.get(poolName);
        if (meta == null) {
            throw new IllegalArgumentException("未找到线程池: " + poolName);
        }
        return meta.executor();
    }

    public int getMinCore(String poolName) {
        PoolMetadata meta = registry.get(poolName);
        if (meta == null) {
            throw new IllegalArgumentException("未找到线程池: " + poolName);
        }
        return meta.minCore();
    }

    public int getMaxCore(String poolName) {
        PoolMetadata meta = registry.get(poolName);
        if (meta == null) {
            throw new IllegalArgumentException("未找到线程池: " + poolName);
        }
        return meta.maxCore();
    }

    public int getCurrentCore(String poolName) {
        PoolMetadata meta = registry.get(poolName);
        if (meta == null) {
            throw new IllegalArgumentException("未找到线程池: " + poolName);
        }
        return meta.executor.getCorePoolSize();
    }

    public int getCurrentMax(String poolName) {
        PoolMetadata meta = registry.get(poolName);
        if (meta == null) {
            throw new IllegalArgumentException("未找到线程池: " + poolName);
        }
        return meta.executor.getMaximumPoolSize();
    }

    public ThreadPoolDetail getPoolDetail(String poolName) {
PoolMetadata meta = registry.get(poolName);
        if (meta == null) {
            throw new IllegalArgumentException("未找到线程池: " + poolName);
        }
        return new ThreadPoolDetail(
                poolName,
                meta.minCore(),
                meta.maxCore(),
                meta.maxQueueSize(),
                meta.executor.getCorePoolSize(),
                meta.executor.getMaximumPoolSize(),
                meta.executor.getActiveCount(),
                meta.executor.getQueue().size(),
                getQueueCapacity(meta.executor.getQueue()),
                (double) meta.executor.getQueue().size() / getQueueCapacity(meta.executor.getQueue()),
                (double) (meta.executor.getPoolSize() - meta.executor.getActiveCount()) / meta.executor.getPoolSize()
        );
    }

    public PoolMetadata getPoolMetadata(String poolName) {
        PoolMetadata meta = registry.get(poolName);
        if (meta == null) {
            throw new IllegalArgumentException("未找到线程池: " + poolName);
        }
        return meta;
    }


    public record PoolMetadata(
            ThreadPoolExecutor executor,
            int minCore,
            int maxCore,
            int maxQueueSize
    ) {
        public PoolMetadata {
            if (minCore > maxCore) {
                throw new IllegalArgumentException("配置范围无效");
            }
        }
    }
}