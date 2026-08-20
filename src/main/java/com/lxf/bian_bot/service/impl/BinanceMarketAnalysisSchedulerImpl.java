package com.lxf.bian_bot.service.impl;

import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.service.BinanceMarketAnalysisScheduler;
import com.lxf.bian_bot.service.BinanceMarketAnalysisService;
import com.lxf.bian_bot.service.BinanceWebSocketStreamsService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceMarketAnalysisSchedulerImpl implements BinanceMarketAnalysisScheduler {

    /**
     * 分析间隔
     * <p>
     * 例如：
     * 10 秒分析一次
     */
    private static final long ANALYSIS_INTERVAL_SECONDS = 10;
    private final BinanceWebSocketStreamsService webSocketStreamsService;
    private final BinanceMarketAnalysisService marketAnalysisService;
    private final BinanceProperties properties;
    private final TickerCache tickerCache;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "binance-market-analysis");
        thread.setDaemon(true);
        return thread;
    });

    public BinanceMarketAnalysisSchedulerImpl(BinanceWebSocketStreamsService webSocketStreamsService, BinanceMarketAnalysisService marketAnalysisService, BinanceProperties properties, TickerCache tickerCache) {
        this.webSocketStreamsService = webSocketStreamsService;
        this.marketAnalysisService = marketAnalysisService;
        this.properties = properties;
        this.tickerCache = tickerCache;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Binance Market Analysis Scheduler 已经启动");
            return;
        }

        log.info("========================================");
        log.info("Binance Market Analysis Scheduler 启动");
        log.info("分析间隔: {} 秒", ANALYSIS_INTERVAL_SECONDS);
        log.info("========================================");
        scheduler.scheduleWithFixedDelay(this::analysis, 0, ANALYSIS_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void analysis() {
        try {
            if (!webSocketStreamsService.isConnected()) {
                log.debug("WebSocket 尚未连接，跳过本次分析");
                return;
            }

            for (String symbol : properties.getSymbols()) {
                BinanceBookTicker ticker = tickerCache.get(symbol);
                if (ticker == null) {
                    log.debug("{} 尚无最新行情，跳过本次分析", symbol);
                    continue;
                }

                log.info("开始市场分析: symbol={}, bid={}, ask={}", ticker.getSymbol(), ticker.getBidPrice(), ticker.getAskPrice());
                marketAnalysisService.analyze(ticker.getSymbol());
            }
        } catch (Exception e) {
            log.error("Binance Market Analysis 执行异常", e);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        log.info("停止 Binance Market Analysis Scheduler");
        scheduler.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
