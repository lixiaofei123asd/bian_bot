package com.lxf.bian_bot.cache;

import com.lxf.bian_bot.model.BinanceSecondKline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 1 秒 K 线内存缓存。
 *
 * 只保存最近一段时间的实时数据，不写 SQLite。
 */
@Component
public class SecondKlineCache {

    private static final int DEFAULT_MAX_SIZE = 120;

    private final ConcurrentMap<String, Deque<BinanceSecondKline>> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BinanceSecondKline> currentKlines = new ConcurrentHashMap<>();

    public void updateCurrent(BinanceSecondKline kline) {
        if (kline == null || kline.getSymbol() == null || kline.getOpenTime() == null) {
            return;
        }
        currentKlines.put(buildKey(kline.getSymbol()), copy(kline));
    }

    public void complete(BinanceSecondKline kline) {
        if (kline == null || kline.getSymbol() == null || kline.getOpenTime() == null) {
            return;
        }
        String key = buildKey(kline.getSymbol());
        Deque<BinanceSecondKline> deque = cache.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            if (!deque.isEmpty() && deque.peekLast().getOpenTime().equals(kline.getOpenTime())) {
                deque.pollLast();
            }
            deque.addLast(copy(kline));
            while (deque.size() > DEFAULT_MAX_SIZE) {
                deque.pollFirst();
            }
        }
        currentKlines.remove(key, currentKlines.get(key));
    }

    /**
     * 获取最近已完成的 1 秒 K 线，按时间从旧到新返回。
     */
    public List<BinanceSecondKline> getRecent(String symbol, int limit) {
        if (symbol == null || symbol.isBlank() || limit <= 0) {
            return List.of();
        }
        Deque<BinanceSecondKline> deque = cache.get(buildKey(symbol));
        if (deque == null) {
            return List.of();
        }
        synchronized (deque) {
            List<BinanceSecondKline> result = new ArrayList<>(deque);
            if (result.size() <= limit) {
                return result;
            }
            return new ArrayList<>(result.subList(result.size() - limit, result.size()));
        }
    }

    /**
     * 获取当前正在形成的 1 秒 K 线。
     */
    public BinanceSecondKline getCurrent(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        BinanceSecondKline kline = currentKlines.get(buildKey(symbol));
        return kline == null ? null : copy(kline);
    }

    /**
     * 获取已完成K线 + 当前正在形成的K线。
     */
    public List<BinanceSecondKline> getRecentIncludingCurrent(String symbol, int limit) {
        List<BinanceSecondKline> result = getRecent(symbol, limit);
        BinanceSecondKline current = getCurrent(symbol);
        if (current == null) {
            return result;
        }
        if (!result.isEmpty() && result.getLast().getOpenTime().equals(current.getOpenTime())) {
            result.set(result.size() - 1, current);
        } else {
            result.add(current);
            if (result.size() > limit) {
                result.removeFirst();
            }
        }
        return result;
    }

    public void clear(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        String key = buildKey(symbol);
        cache.remove(key);
        currentKlines.remove(key);
    }

    private String buildKey(String symbol) {
        return symbol.trim().toUpperCase();
    }

    private BinanceSecondKline copy(BinanceSecondKline source) {
        BinanceSecondKline target = new BinanceSecondKline();
        target.setSymbol(source.getSymbol());
        target.setOpenTime(source.getOpenTime());
        target.setOpenPrice(source.getOpenPrice());
        target.setHighPrice(source.getHighPrice());
        target.setLowPrice(source.getLowPrice());
        target.setClosePrice(source.getClosePrice());
        target.setVolume(source.getVolume());
        target.setTradeCount(source.getTradeCount());
        target.setLastTradeTime(source.getLastTradeTime());
        return target;
    }
}
