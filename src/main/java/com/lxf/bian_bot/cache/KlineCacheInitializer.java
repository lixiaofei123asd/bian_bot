package com.lxf.bian_bot.cache;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.repository.KlineRepository;
import com.lxf.bian_bot.service.BinanceMarketService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KlineCacheInitializer {

    private static final String KLINE_INTERVAL = "1m";
    private static final long KLINE_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_KLINE_SIZE = 1_000;
    private final BinanceProperties properties;
    private final SymbolRuleCache symbolRuleCache;
    private final KlineRepository klineRepository;
    private final KlineCache klineCache;
    private final BinanceMarketService marketService;

    public KlineCacheInitializer(BinanceProperties properties, SymbolRuleCache symbolRuleCache, KlineRepository klineRepository, KlineCache klineCache, BinanceMarketService marketService) {
        this.properties = properties;
        this.symbolRuleCache = symbolRuleCache;
        this.klineRepository = klineRepository;
        this.klineCache = klineCache;
        this.marketService = marketService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        List<String> symbols = symbolRuleCache.filterValidSymbols(properties.getSymbols());
        if (symbols.isEmpty()) {
            log.warn("没有有效的 Binance 交易对，跳过 KlineCache 启动恢复");
            return;
        }

        log.info("开始恢复 Binance KlineCache: symbols={}, interval={}, maxSize={}", symbols, KLINE_INTERVAL, MAX_KLINE_SIZE);
        for (String symbol : symbols) {
            try {
                initializeSymbol(symbol);
            } catch (Exception e) {
                log.error("KlineCache 启动恢复异常: symbol={}, interval={}", symbol, KLINE_INTERVAL, e);
            }
        }
        log.info("Binance KlineCache 启动恢复完成");
    }

    private void initializeSymbol(String symbol) {
        List<BinanceKline> cachedKlines = klineRepository.findLatest(symbol, KLINE_INTERVAL, MAX_KLINE_SIZE);
        long currentOpenTime = System.currentTimeMillis() / KLINE_INTERVAL_MILLIS * KLINE_INTERVAL_MILLIS;

        if (cachedKlines.isEmpty()) {
            log.info("SQLite 没有 K线数据，获取最近{}根: symbol={}", MAX_KLINE_SIZE, symbol);
            List<BinanceKline> latestKlines = marketService.refreshLatestKlines(symbol, Interval.INTERVAL_1m, (long) MAX_KLINE_SIZE);
            saveAndCache(symbol, latestKlines);
            return;
        }

        BinanceKline latestCachedKline = cachedKlines.getLast();
        long gap = Math.max(0L, (currentOpenTime - latestCachedKline.getOpenTime()) / KLINE_INTERVAL_MILLIS);
        int refreshLimit = (int) Math.min(MAX_KLINE_SIZE, Math.max(2L, gap + 2));

        log.info("SQLite K线恢复检查: symbol={}, latestOpenTime={}, currentOpenTime={}, gap={}, refreshLimit={}", symbol, latestCachedKline.getOpenTime(), currentOpenTime, gap, refreshLimit);

        List<BinanceKline> latestKlines = marketService.refreshLatestKlines(symbol, Interval.INTERVAL_1m, (long) refreshLimit);
        Map<Long, BinanceKline> klineMap = cachedKlines.stream().collect(Collectors.toMap(BinanceKline::getOpenTime, Function.identity(), (oldValue, newValue) -> newValue));
        latestKlines.forEach(kline -> klineMap.put(kline.getOpenTime(), kline));

        List<BinanceKline> mergedKlines = klineMap.values().stream()
                .sorted(Comparator.comparing(BinanceKline::getOpenTime))
                .toList();
        if (mergedKlines.size() > MAX_KLINE_SIZE) {
            mergedKlines = new ArrayList<>(mergedKlines.subList(mergedKlines.size() - MAX_KLINE_SIZE, mergedKlines.size()));
        }

        saveAndCache(symbol, mergedKlines);
    }

    private void saveAndCache(String symbol, List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            log.warn("Binance K线恢复结果为空: symbol={}", symbol);
            return;
        }

        klineCache.put(symbol, KLINE_INTERVAL, klines);
        log.info("KlineCache 恢复完成: symbol={}, interval={}, size={}, latestOpenTime={}", symbol, KLINE_INTERVAL, klines.size(), klines.getLast().getOpenTime());
    }
}
