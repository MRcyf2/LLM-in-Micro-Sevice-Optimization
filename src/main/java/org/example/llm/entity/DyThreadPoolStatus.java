package org.example.llm.entity;

import lombok.Data;

@Data
public class DyThreadPoolStatus {
    private int coreSize;
    private int maxSize;
    private int queueCapacity;//不可变
}
