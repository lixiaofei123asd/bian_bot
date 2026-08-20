package com.lxf.bian_bot.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "strategy")
public class StrategyProperties {

    // 每个币种对应一个策略
    private Map<String, String> symbols = new LinkedHashMap<>();
}
