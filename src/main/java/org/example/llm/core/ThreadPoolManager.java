package org.example.llm.core;

import org.example.llm.entity.ThreadPoolStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

@Component
public class ThreadPoolManager {

    @Autowired
    private ThreadPoolExecutor dynamicThreadPool;  // 动态线程池实例

    public void setCorePoolSize(int corePoolSize) {
        dynamicThreadPool.setCorePoolSize(corePoolSize);
    }
    public void setMaxPoolSize(int maxPoolSize) {
        dynamicThreadPool.setMaximumPoolSize(maxPoolSize);
    }

    public ThreadPoolStatus getThreadPoolStatus() {
        return new ThreadPoolStatus(
                dynamicThreadPool.getCorePoolSize(),
                dynamicThreadPool.getMaximumPoolSize(),
                dynamicThreadPool.getActiveCount(),
                dynamicThreadPool.getQueue().size(),
                dynamicThreadPool.getCompletedTaskCount(),
                dynamicThreadPool.getTaskCount(),
                dynamicThreadPool.getLargestPoolSize(),
                dynamicThreadPool.getKeepAliveTime(TimeUnit.SECONDS),
                dynamicThreadPool.getRejectedExecutionHandler().getClass().getSimpleName(),
                dynamicThreadPool.getQueue().getClass().getSimpleName()

        );
    }


}
