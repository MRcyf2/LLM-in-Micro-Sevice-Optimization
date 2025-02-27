package org.example.llm.entity;

import lombok.Data;
import lombok.Setter;

@Data
public class ThreadPoolStatus {
    private final int corePoolSize; // 核心线程数
    private final int maxPoolSize;  // 最大线程数
    private final int activeThreads;    // 活跃线程数
    private final int queueSize;    // 队列大小
    private final long completedTaskCount;    // 完成任务数
    private final long taskCount; // 任务总数
    private final int largestPoolSize;    // 最大线程数
    private final long keepAliveTime; // 线程存活时间
    private final String rejectedExecutionHandler;    // 拒绝策略
    private final String workQueue;   // 队列类型


    // normal
    public ThreadPoolStatus(int corePoolSize, int maxPoolSize, int activeThreads, int queueSize, long completedTaskCount, long taskCount, int largestPoolSize, long keepAliveTime, String rejectedExecutionHandler, String workQueue) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.activeThreads = activeThreads;
        this.queueSize = queueSize;
        this.completedTaskCount = completedTaskCount;
        this.taskCount = taskCount;
        this.largestPoolSize = largestPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.rejectedExecutionHandler = rejectedExecutionHandler;
        this.workQueue = workQueue;
    }
}