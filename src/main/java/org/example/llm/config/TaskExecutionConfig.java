package org.example.llm.config;

import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.example.llm.core.DeepseekClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
public class TaskExecutionConfig {

    // 与@Async("tuningAsyncPool") 同名的线程池Bean
    @Bean("tuningAsyncPool")
    public ThreadPoolTaskExecutor tuningAsyncPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("deepseek-tuning-"); // 便于日志追踪
        executor.initialize();
        return executor;
    }

    // 注入DeepseekClient时提供Executor
    @Bean
    public DeepseekClient deepseekClient(
            @Qualifier("tuningAsyncPool") Executor executor // 若DeepseekClient需要异步执行器
    ) {
        return new DeepseekClient(executor); // 若DeepseekClient构造函数需要Executor
    }
}