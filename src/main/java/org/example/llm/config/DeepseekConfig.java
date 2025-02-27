package org.example.llm.config;

import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

@Configuration
@NacosConfigurationProperties(prefix = "deepseek", dataId = "thread-pool-config", autoRefreshed = true)
@Data
public class DeepseekConfig {

    private String baseUrl = "https://api.deepseek.com";

    private String apiKey ="sk-7287e9b322294da998d78dac23273499";

    private String model = "deepseek-reasoner";

    private int timeout = 10;
}