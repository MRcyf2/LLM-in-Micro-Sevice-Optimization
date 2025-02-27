package org.example.llm;

import org.example.llm.core.DyThreadPool;
import org.junit.jupiter.api.Test;
import java.lang.annotation.Annotation;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

public class DyThreadPoolAnnotationTest {

    @Test
    public void testAnnotationDefinition() {
        // 1. 获取类中带注解的字段（示例字段）
        class TestClass {
            @DyThreadPool(poolName = "testPool")
            private ThreadPoolExecutor threadPool;
        }

        // 2. 通过反射获取注解实例
        Annotation[] annotations;
        try {
            annotations = TestClass.class.getDeclaredField("threadPool").getAnnotations();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        // 3. 查找 @DyThreadPool 注解
        DyThreadPool dyThreadPool = null;
        for (Annotation annotation : annotations) {
            if (annotation instanceof DyThreadPool) {
                dyThreadPool = (DyThreadPool) annotation;
                break;
            }
        }

        // 4. 验证注解属性
        assertNotNull(dyThreadPool, "@DyThreadPool 注解未正确应用");
        assertEquals("testPool", dyThreadPool.poolName(), "poolName 属性值不匹配");
    }
}