package com.lxf.bian_bot.analysis.service;

import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.service.BinanceWebSocketStreamsService;
import com.lxf.bian_bot.trading.service.BinanceTradingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceMarketScheduledService {

    private final BinanceMarketAnalysisService marketAnalysisService;
    private final BinanceTradingService tradingService;
    private final BinanceWebSocketStreamsService webSocketStreamsService;
    private final BinanceProperties properties;
    private final TickerCache tickerCache;

    public BinanceMarketScheduledService(BinanceMarketAnalysisService marketAnalysisService, BinanceTradingService tradingService, BinanceWebSocketStreamsService webSocketStreamsService, BinanceProperties properties, TickerCache tickerCache) {
        this.marketAnalysisService = marketAnalysisService;
        this.tradingService = tradingService;
        this.webSocketStreamsService = webSocketStreamsService;
        this.properties = properties;
        this.tickerCache = tickerCache;
    }

    // 每10秒执行一次所有币种的市场分析和交易执行
    @Scheduled(cron = "${scheduling.task.cron}")
    public void scheduledAnalysis() {
        if (!webSocketStreamsService.isConnected()) {
            log.debug("WebSocket 尚未连接，跳过本次分析");
            return;
        }

        for (String symbol : properties.getSymbols()) {
            try {
                BinanceBookTicker ticker = tickerCache.get(symbol);
                if (ticker == null) {
                    log.debug("{} 尚无最新行情，跳过本次分析", symbol);
                    continue;
                }

                TradingDecision decision = marketAnalysisService.analyze(symbol);
                log.info("{} 当前交易决策: {}", symbol, decision.getDecision());
                tradingService.execute(decision);
            } catch (Exception e) {
                log.error("Binance Market Analysis 执行异常: symbol={}", symbol, e);
            }
        }
    }
}
