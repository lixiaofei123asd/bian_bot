package com.lxf.bian_bot.config.model;

import java.util.List;
import java.util.Map;

/**
 * 策略业务配置。
 *
 * <p>该类只提供策略业务需要的配置，不负责读取 application.yaml。</p>
 */
public class StrategyConfig {

    private final Map<String, String> symbols;

    public StrategyConfig(Map<String, String> symbols) {
        this.symbols = symbols;
    }

    /**
     * 获取指定交易对对应的策略名称。
     */
    public String getStrategyName(String symbol) {
        if (symbol == null) {
            return null;
        }
        return symbols.get(symbol.toUpperCase());
    }

    /**
     * 获取当前策略负责的所有交易对。
     */
    public List<String> getSymbolsForStrategy(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return List.of();
        }

        return symbols.entrySet().stream()
                .filter(entry -> strategyName.equalsIgnoreCase(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
