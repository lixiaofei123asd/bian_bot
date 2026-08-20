package com.lxf.bian_bot.indicator;

import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class IndicatorResultStore {

    /**
     * 不同交易对分别保存分析结果
     * <p>
     * BTCUSDT
     * ETHUSDT
     * SOLUSDT
     * ...
     */
    private final ConcurrentMap<String, BinanceMarketAnalysis> results = new ConcurrentHashMap<>();

    /**
     * 保存最新分析结果
     */
    public void update(String symbol, BinanceMarketAnalysis analysis) {
        if (symbol == null || analysis == null) {
            return;
        }
        results.put(symbol.toUpperCase(), analysis);
    }

    /**
     * 获取指定交易对最新分析结果
     */
    public BinanceMarketAnalysis get(String symbol) {
        if (symbol == null) {
            return null;
        }
        return results.get(symbol.toUpperCase());
    }

    /**
     * 删除
     */
    public void remove(String symbol) {
        if (symbol == null) {
            return;
        }
        results.remove(symbol.toUpperCase());
    }

    /**
     * 清空
     */
    public void clear() {
        results.clear();
    }
}
