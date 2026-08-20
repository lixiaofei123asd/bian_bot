package com.lxf.bian_bot.analysis.service;

import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.service.BinanceWebSocketStreamsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceMarketScheduledService {

    private final BinanceMarketAnalysisService marketAnalysisService;
    private final BinanceWebSocketStreamsService webSocketStreamsService;
    private final BinanceProperties properties;
    private final TickerCache tickerCache;

    public BinanceMarketScheduledService(BinanceMarketAnalysisService marketAnalysisService, BinanceWebSocketStreamsService webSocketStreamsService, BinanceProperties properties, TickerCache tickerCache) {
        this.marketAnalysisService = marketAnalysisService;
        this.webSocketStreamsService = webSocketStreamsService;
        this.properties = properties;
        this.tickerCache = tickerCache;
    }

    // 每10秒执行一次所有币种的市场分析
    @Scheduled(fixedDelay = 10000)
    public void scheduledAnalysis() {
        if (!webSocketStreamsService.isConnected()) {
            log.debug("WebSocket 尚未连接，跳过本次分析");
            return;
        }

        try {
            for (String symbol : properties.getSymbols()) {
                BinanceBookTicker ticker = tickerCache.get(symbol);
                if (ticker == null) {
                    log.debug("{} 尚无最新行情，跳过本次分析", symbol);
                    continue;
                }

                TradingDecision decision = marketAnalysisService.analyze(symbol);
                log.info("{} 当前交易决策: {}", symbol, decision.getDecision());
            }
        } catch (Exception e) {
            log.error("Binance Market Analysis 执行异常", e);
        }
    }
}
