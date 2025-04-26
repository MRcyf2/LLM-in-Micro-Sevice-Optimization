package org.example.llm.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.type.MethodMetadata;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

@Component
@Slf4j
@RequiredArgsConstructor
public class DyThreadPoolBeanProcessor implements BeanPostProcessor {
    private final ThreadPoolRegistry registry;
    private final ConfigurableListableBeanFactory beanFactory;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ThreadPoolExecutor) {
            processThreadPoolBean(beanName, (ThreadPoolExecutor) bean);
        }
        return bean;
    }

    private void processThreadPoolBean(String beanName, ThreadPoolExecutor executor) {
        try {
            BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
            if (beanDef.getSource() instanceof MethodMetadata) {
                MethodMetadata methodMeta = (MethodMetadata) beanDef.getSource();
                String methodName = methodMeta.getMethodName();
                Class<?> configClass = Class.forName(methodMeta.getDeclaringClassName());

                // 查找@Bean方法
                Method beanMethod = Arrays.stream(configClass.getDeclaredMethods())
                        .filter(m -> m.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow();

                // 获取@DyThreadPool注解
                DyThreadPool annotation = beanMethod.getAnnotation(DyThreadPool.class);
                if (annotation != null) {
                    registry.register(
                            annotation.poolName(),
                            executor,
                            annotation.minCore(),
                            annotation.maxCore(),
                            annotation.maxQueueSize()
                    );
                    log.info("成功注册动态线程池: {}", annotation.poolName());
                }
            }
        } catch (Exception e) {
            log.error("处理线程池Bean失败: {}", beanName, e);
        }
    }
}