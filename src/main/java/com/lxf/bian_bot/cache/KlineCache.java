package com.lxf.bian_bot.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.repository.KlineRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KlineCache {

    private final Cache<String, List<BinanceKline>> cache = Caffeine.newBuilder().build();
    private final KlineRepository klineRepository;

    public KlineCache(KlineRepository klineRepository) {
        this.klineRepository = klineRepository;
        this.klineRepository.initialize();
    }

    public void put(String symbol, String interval, List<BinanceKline> klines) {
        if (symbol == null || symbol.isBlank() || interval == null || interval.isBlank() || klines == null) {
            return;
        }
        List<BinanceKline> copy = new ArrayList<>(klines);
        cache.put(buildKey(symbol, interval), copy);
        klineRepository.saveAll(interval, copy);
        log.debug("KlineCache 初始化完成并持久化: symbol={}, interval={}, size={}", symbol, interval, copy.size());
    }

    public List<BinanceKline> get(String symbol, String interval) {
        if (symbol == null || symbol.isBlank() || interval == null || interval.isBlank()) {
            return Collections.emptyList();
        }
        List<BinanceKline> klines = cache.getIfPresent(buildKey(symbol, interval));
        if (klines == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(klines);
    }

    public void update(String symbol, String interval, BinanceKline kline, int maxSize) {
        if (symbol == null || symbol.isBlank() || interval == null || interval.isBlank() || kline == null) {
            return;
        }
        String key = buildKey(symbol, interval);
        List<BinanceKline> klines = new ArrayList<>(get(symbol, interval));
        if (!klines.isEmpty() && klines.getLast().getOpenTime().equals(kline.getOpenTime())) {
            klines.set(klines.size() - 1, kline);
        } else {
            klines.add(kline);
        }
        if (klines.size() > maxSize) {
            klines = new ArrayList<>(klines.subList(klines.size() - maxSize, klines.size()));
        }
        cache.put(key, klines);
        if (isClosed(kline)) {
            klineRepository.save(interval, kline);
            log.debug("K线已收盘并持久化: symbol={}, interval={}, openTime={}", symbol, interval, kline.getOpenTime());
        }
    }

    public boolean contains(String symbol, String interval) {
        return cache.getIfPresent(buildKey(symbol, interval)) != null;
    }

    public void remove(String symbol, String interval) {
        cache.invalidate(buildKey(symbol, interval));
    }

    public void clear() {
        cache.invalidateAll();
        log.info("KlineCache 已清空");
    }

    private boolean isClosed(BinanceKline kline) {
        return kline.getCloseTime() != null && System.currentTimeMillis() >= kline.getCloseTime();
    }

    private String buildKey(String symbol, String interval) {
        return symbol.trim().toUpperCase() + ":" + interval.trim().toLowerCase();
    }
}
