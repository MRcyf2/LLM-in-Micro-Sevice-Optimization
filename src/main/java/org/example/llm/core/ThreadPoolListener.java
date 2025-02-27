package org.example.llm.core;

import org.example.llm.entity.ThreadPoolStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ThreadPoolListener {

    @Autowired
    private ThreadPoolManager threadPoolManager;
    @Autowired
    private DeepseekClient deepseekClient;

    @Scheduled(fixedRate = 1000*30)//30秒
    public void GetAndSet() throws Exception {
        //1.获取线程池状态
        ThreadPoolStatus status = threadPoolManager.getThreadPoolStatus();
        //2.交由llm调节
        List<Integer> list=deepseekClient.analyzeThreadPool(status);
        //3.设置线程池参数
        threadPoolManager.setCorePoolSize(list.get(0));
        threadPoolManager.setMaxPoolSize(list.get(1));
    }


}
