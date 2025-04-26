package org.example.llm.config;

import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.example.llm.core.DyThreadPool;
import org.example.llm.entity.ResizableLinkedBlockingQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @DyThreadPool(
            poolName = "orderServicePool",
            minCore = 8,
            maxCore = 32,
            maxQueueSize = 500
    )
    @Bean
    public ThreadPoolExecutor orderThreadPool() {
        return new ThreadPoolExecutor(
                8, 32, 60L, TimeUnit.SECONDS,
                new ResizableLinkedBlockingQueue<>(500), // 自定义可调整队列
                new NamedThreadFactory("order-pool")
        );
    }



    @DyThreadPool(
            poolName = "paymentServicePool",
            minCore = 2,
            maxCore = 16
    )
    @Bean
    public ThreadPoolExecutor paymentServicePool() {
        return new ThreadPoolExecutor(
                4, 16, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new NamedThreadFactory("payment-pool")
        );
    }
}