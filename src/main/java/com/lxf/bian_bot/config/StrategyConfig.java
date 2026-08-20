package com.lxf.bian_bot.config;

import java.util.Map;

public class StrategyConfig {

    private final Map<String, String> symbols;

    public StrategyConfig(Map<String, String> symbols) {
        this.symbols = symbols;
    }

    public String getStrategyName(String symbol) {
        return symbols.get(symbol.toUpperCase());
    }
}
