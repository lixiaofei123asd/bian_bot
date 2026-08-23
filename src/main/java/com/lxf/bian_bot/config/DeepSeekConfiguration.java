package com.lxf.bian_bot.config;

import com.lxf.bian_bot.config.model.DeepSeekConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** DeepSeek 配置转换。 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekConfiguration {

    private final DeepSeekProperties deepSeekProperties;

    /** 将 application.yaml 中的 DeepSeek 配置转换成业务层使用的 Bean。 */
    @Bean
    public DeepSeekConfig deepSeekConfig() {
        return new DeepSeekConfig(
                deepSeekProperties.getApiKey(),
                deepSeekProperties.getBaseUrl(),
                deepSeekProperties.getModel());
    }
}
