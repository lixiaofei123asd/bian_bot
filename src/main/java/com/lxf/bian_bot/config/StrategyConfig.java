package com.lxf.bian_bot.config;

import java.util.Map;

public record StrategyConfig(Map<String, String> symbols) {

    // 获取指定币种配置的策略名称
    public String getStrategyName(String symbol) {
        if (symbol == null) {
            return null;
        }
        return symbols.get(symbol.toUpperCase());
    }
}
