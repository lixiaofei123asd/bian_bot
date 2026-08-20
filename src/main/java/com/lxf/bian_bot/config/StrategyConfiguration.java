package com.lxf.bian_bot.config;

import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(StrategyProperties.class)
public class StrategyConfiguration {

    private final StrategyProperties strategyProperties;

    // 获取指定币种配置的策略名称
    public String getStrategyName(String symbol) {
        if (symbol == null) {
            return null;
        }
        return strategyProperties.getSymbols().get(symbol.toUpperCase(Locale.ROOT));
    }

    // 获取全部币种策略配置
    public Map<String, String> getStrategySymbols() {
        return strategyProperties.getSymbols();
    }
}
