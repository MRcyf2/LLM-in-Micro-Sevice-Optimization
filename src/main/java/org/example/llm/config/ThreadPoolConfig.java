package org.example.llm.config;

import lombok.Data;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "dynamicThreadPool")
    public ThreadPoolExecutor dynamicThreadPool() {

        int initialCoreSize = 10;//核心
        int initialMaxSize = 20;//最大
        int initialQueueCapacity = 100;//队列

        return new ThreadPoolExecutor(
                initialCoreSize,
                initialMaxSize,
                60L,//空闲线程存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(initialQueueCapacity),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
