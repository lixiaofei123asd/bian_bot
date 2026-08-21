package com.lxf.bian_bot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TradingProperties.class)
public class TradingConfiguration {

    private final TradingProperties tradingProperties;

    // 将配置文件中的交易配置转换成业务层使用的 Bean
    @Bean
    public TradingConfig tradingConfig() {
        return new TradingConfig(
                tradingProperties.isEnabled(),
                tradingProperties.getUsdtAmount(),
                tradingProperties.getLeverage(),
                tradingProperties.isAutoSetLeverage()
        );
    }
}
