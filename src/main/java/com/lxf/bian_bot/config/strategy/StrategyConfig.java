package com.lxf.bian_bot.config.strategy;

import java.util.List;
import java.util.Map;

/** 策略业务配置。 */
public class StrategyConfig {

    private final Map<String, String> symbols;

    public StrategyConfig(Map<String, String> symbols) {
        this.symbols = symbols;
    }

    public Map<String, String> getSymbols() {
        return symbols;
    }

    public String getStrategyName(String symbol) {
        if (symbol == null) return null;
        return symbols.get(symbol.toUpperCase());
    }

    public List<String> getSymbolsForStrategy(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) return List.of();
        return symbols.entrySet().stream()
                .filter(entry -> strategyName.equalsIgnoreCase(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
