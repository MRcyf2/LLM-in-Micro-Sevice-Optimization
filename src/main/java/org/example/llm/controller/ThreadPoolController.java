package org.example.llm.controller;

import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.llm.core.ThreadPoolRegistry;
import org.example.llm.core.ThreadPoolTuningScheduler;
import org.example.llm.entity.R;
import org.example.llm.entity.ThreadPoolDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/thread-pool-test")
public class ThreadPoolController {
    private final ThreadPoolRegistry registry;
    private final MeterRegistry meterRegistry;
    private final ThreadPoolTuningScheduler tuningScheduler;

    public ThreadPoolController(ThreadPoolRegistry registry, MeterRegistry meterRegistry, ThreadPoolTuningScheduler tuningScheduler) {
        this.registry = registry;
        this.meterRegistry = meterRegistry;
        this.tuningScheduler = tuningScheduler;
    }

    @GetMapping("/status")
    public Map<String, Object> getPoolStatus() {
        return registry.getAllPoolDetails().stream()
                .collect(Collectors.toMap(
                        ThreadPoolDetail::getPoolName,
                        d -> new LinkedHashMap<String, Object>() {{
                            put("config", Map.of(
                                    "minCore", d.getConfigMinCore(),
                                    "maxCore", d.getConfigMaxCore(),
                                    "maxQueue", d.getConfigMaxQueue()
                            ));
                            put("runtime", Map.of(
                                    "coreSize", d.getCurrentCore(),
                                    "maxSize", d.getCurrentMax(),
                                    "activeThreads", d.getActiveThreads(),
                                    "queueSize", d.getQueueSize()
                            ));
                        }}
                ));
    }

    @PostMapping("/start-auto-tuning")
    public R<?> startAutoTuning(
            @RequestParam String poolName
    ) {
        tuningScheduler.startAutoTuning(poolName);
        return R.success("定时调优已启动，每5秒执行一次");
    }

    // 停止定时调优
    @PostMapping("/stop-auto-tuning")
    public R<?> stopAutoTuning(
            @RequestParam String poolName
    ) {
        tuningScheduler.stopAutoTuning(poolName);
        return R.success("定时调优已停止");
    }

    // 获取线程池状态
    @GetMapping("/status/{poolName}")
    public R<ThreadPoolDetail> getPoolStatus(
            @PathVariable String poolName,
            @Autowired ThreadPoolRegistry registry
    ) {
        ThreadPoolDetail detail = registry.getPoolDetail(poolName);
        return R.success(detail);
    }


    // AI自动调整参数接口
    @PostMapping("/adjust-ai")
    public R<?> autoAdjust(
            @RequestParam String poolName
    ) {
        try {
            registry.adjustByAi(poolName);
            return R.success(Map.of(
                    "status", "success",
                    "message", "参数调整成功",
                    "pool", poolName
            ));
        } catch (Exception e) {
            return R.error(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    // 压力测试接口
//    @PostMapping("/stress-test")
//    public R<?> stressTest(
//            @RequestParam(defaultValue = "orderServicePool") String poolName ,
//            @RequestParam int taskCount
//    ) {
//        ThreadPoolExecutor pool = registry.getExecutor(poolName);
//        for (int i = 0; i < taskCount; i++) {
//            pool.execute(() -> {
//                try {
//                    Thread.sleep(1000); // 模拟耗时任务
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                }
//            });
//        }
//        return R.success(Map.of(
//                "status", "success",
//                "submittedTasks", taskCount,
//                "currentQueueSize", pool.getQueue().size()
//        ));
//    }
//
//    @PostMapping("/stress-test2")
//    public CompletableFuture<R<?>> stressTest(
//            @RequestParam(defaultValue = "orderServicePool") String poolName,
//            @RequestParam int taskCount,
//            @RequestParam(defaultValue = "100") int tasksPerSecond) {
//
//        // 1. 获取线程池实例
//        ThreadPoolExecutor pool = registry.getExecutor(poolName);
//
//        // 2. 初始化计数器
//        AtomicInteger submitted = new AtomicInteger(0);
//        AtomicInteger rejected = new AtomicInteger(0);
//        AtomicInteger completed = new AtomicInteger(0);
//
//        // 3. 压力控制
//        RateLimiter limiter = RateLimiter.create(tasksPerSecond);
//
//        // 4. 异步提交任务
//        return CompletableFuture.supplyAsync(() -> {
//            for (int i = 0; i < taskCount; i++) {
//                try {
//                    // 4.1 控制提交速率
//                    limiter.acquire();
//
//                    // 4.2 提交任务（捕获拒绝异常）
//                    pool.execute(() -> {
//                        try {
//                            // 模拟业务处理
//                            Thread.sleep(1000);
//                            completed.incrementAndGet(); // 记录成功完成的任务
//                        } catch (InterruptedException e) {
//                            Thread.currentThread().interrupt();
//                        }
//                    });
//                    submitted.incrementAndGet();
//                } catch (RejectedExecutionException e) {
//                    rejected.incrementAndGet();
//                    // 4.3 记录拒绝指标（可选）
//                    meterRegistry.counter("stress.rejected.tasks", "pool", poolName).increment();
//                }
//            }
//
//            // 5. 返回实时统计结果
//            return R.success(Map.of(
//                    "submittedTasks", submitted.get(),
//                    "rejectedTasks", rejected.get(),
//                    "completedTasks", completed.get(),
//                    "currentQueueSize", pool.getQueue().size(),
//                    "activeThreads", pool.getActiveCount(),
//                    "remainingCapacity", pool.getQueue().remainingCapacity()
//            ));
//        }, pool); // 使用线程池执行提交逻辑（非HTTP线程）
//    }

    @PostMapping("/dynamic-stress-test")
    public CompletableFuture<R<?>> dynamicStressTest(
            @RequestParam(defaultValue = "orderServicePool") String poolName,
            @RequestParam int taskCount,
            @RequestParam(defaultValue = "100") int tasksPerSecond
    ) {
        // 1. 确保调优已启动（若未启动则自动开启）
        tuningScheduler.startAutoTuning(poolName);

        // 2. 执行压力测试（与原有逻辑一致）
        return stressTestCore(poolName, taskCount, tasksPerSecond, true);
    }

    // ================== 静态线程池对比接口 ==================
    @PostMapping("/static-stress-test")
    public CompletableFuture<R<?>> staticStressTest(
            @RequestParam(defaultValue = "orderServicePool") String poolName,
            @RequestParam int taskCount,
            @RequestParam(defaultValue = "100") int tasksPerSecond,
            @RequestParam int fixedCore // 静态核心线程数（需≥minCore且≤maxCore）
    ) {
        // 1. 停止调优并固定参数
        tuningScheduler.stopAutoTuning(poolName);
        enforceStaticConfig(poolName, fixedCore); // 自定义固定配置方法

        // 2. 执行压力测试（禁止参数变化）
        return stressTestCore(poolName, taskCount, tasksPerSecond, false);
    }

    // 压力测试核心逻辑（抽取公共方法）
    private CompletableFuture<R<?>> stressTestCore(
            String poolName,
            int taskCount,
            int tasksPerSecond,
            boolean isDynamic // 是否允许动态调优
    ) {
        ThreadPoolExecutor pool = registry.getExecutor(poolName);
        AtomicInteger submitted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);
        RateLimiter limiter = RateLimiter.create(tasksPerSecond);

        AtomicLong totalWaitingTime = new AtomicLong(0);// 记录总排队时间

        return CompletableFuture.supplyAsync(() -> {
            // 记录初始配置（用于对比）
            int initialCore = pool.getCorePoolSize();
            int initialMax = pool.getMaximumPoolSize();

            for (int i = 0; i < taskCount; i++) {
                try {
                    limiter.acquire();
                    long enqueueTime = System.currentTimeMillis();// 记录入队时间
                    pool.execute(() -> {
                        long waitingTime = System.currentTimeMillis() - enqueueTime;// 计算排队时间
                        totalWaitingTime.addAndGet(waitingTime);// 累加排队时间
                        try {
                            Thread.sleep(1000); // 模拟任务
                            completed.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    submitted.incrementAndGet();
                } catch (RejectedExecutionException e) {
                    rejected.incrementAndGet();
                    meterRegistry.counter("stress.rejected.tasks", "pool", poolName, "mode", isDynamic ? "dynamic" : "static").increment();
                }
            }
            //等待任务完成
            while (completed.get() + rejected.get() < submitted.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            double taskCompletionRate = (double) completed.get() / submitted.get();
            double averageWaitingTime = submitted.get() > 0 ? (double) totalWaitingTime.get() / submitted.get() : 0;

            System.out.println("任务完成率: " + taskCompletionRate);
            System.out.println("平均排队时间: " + averageWaitingTime + " ms");


            // 返回差异化结果
            return R.success(Map.of(
                    "testMode", isDynamic ? "动态调优" : "静态固定",
                    "initialCore", initialCore,
                    "finalCore", pool.getCorePoolSize(), // 动态模式下会变化，静态固定
                    "submitted", submitted.get(),
                    "rejected", rejected.get(),
                    "queueSize", pool.getQueue().size(),
                    "isAutoTuning", tuningScheduler.isTuningActive(poolName)
            ));
        }, pool);
    }

    // 固定静态配置
    private void enforceStaticConfig(String poolName, int fixedCore) {
        ThreadPoolRegistry.PoolMetadata meta = registry.getPoolMetadata(poolName);
        int validCore = Math.min(Math.max(meta.minCore(), fixedCore), meta.maxCore());
        registry.adjust(poolName, validCore, validCore);
    }

    // 手动调整参数接口
//    @PostMapping("/adjust")
//    public R<?> manualAdjust(
//            @RequestParam String poolName,
//            @RequestParam int newCore,
//            @RequestParam int newMax
//    ) {
//        try {
//            registry.adjust(poolName, newCore, newMax);
//            return R.success(Map.of(
//                    "status", "success",
//                    "message", "参数调整成功",
//                    "pool", poolName,
//                    "newCore", newCore,
//                    "newMax", newMax
//            ));
//        } catch (Exception e) {
//            return R.error(Map.of(
//                    "status", "error",
//                    "message", e.getMessage()
//            ));
//        }
//    }
}
