package com.lxf.bian_bot.cache;

import com.lxf.bian_bot.thirdparty.binance.model.BinanceBookTicker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TickerCache {

    /**
     * 最新行情
     * <p>
     * key:
     * BTCUSDT
     * <p>
     * value:
     * 最新 BinanceBookTicker
     */
    private final Map<String, BinanceBookTicker> cache = new ConcurrentHashMap<>();

    /**
     * 更新行情
     */
    public void update(BinanceBookTicker ticker) {
        if (ticker == null || ticker.getSymbol() == null || ticker.getSymbol().isBlank()) {
            return;
        }
        String symbol = ticker.getSymbol().toUpperCase();
        cache.put(symbol, ticker);
    }

    /**
     * 获取单个交易对最新行情
     */
    public BinanceBookTicker get(String symbol) {
        if (symbol == null) {
            return null;
        }
        return cache.get(symbol.toUpperCase());
    }

    /**
     * 判断是否存在
     */
    public boolean contains(String symbol) {

        if (symbol == null) {
            return false;
        }

        return cache.containsKey(symbol.toUpperCase());
    }

    /**
     * 删除
     */
    public void remove(String symbol) {

        if (symbol == null) {
            return;
        }

        cache.remove(symbol.toUpperCase());
    }

    /**
     * 获取所有行情
     * <p>
     * 返回快照，避免外部直接修改内部 Map
     */
    public Map<String, BinanceBookTicker> getAll() {
        return Map.copyOf(cache);
    }

    /**
     * 当前缓存数量
     */
    public int size() {
        return cache.size();
    }

    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * 清空缓存
     */
    public void clear() {
        cache.clear();
        log.info("TickerCache 已清空");
    }
}
