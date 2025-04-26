package org.example.llm.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ThreadPoolDetail {
    private String poolName;

    private int configMinCore;
    private int configMaxCore;
    private int configMaxQueue;

    private int currentCore;
    private int currentMax;
    private int activeThreads;
    private int queueSize;

    private int queueCapacity;
    private double queueUtilization;
    private double idleRatio;
}