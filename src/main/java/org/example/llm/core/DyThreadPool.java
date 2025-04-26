package org.example.llm.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义线程池注解
 * @author fifi
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DyThreadPool {
    /**
     * 线程池唯一标识
     */
    String poolName();

    /**
     * 核心线程数最小值
     */
    int minCore() default 10;

    /**
     * 线程数最大值
     */
    int maxCore() default 100;

    /**
     * 队列容量动态调整范围
     */
    int maxQueueSize() default 200;
}