package com.lxf.bian_bot.scheduler;

import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.service.BinanceTradingDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BinanceTradingDecisionScheduler {

    private final BinanceTradingDecisionService decisionService;
    private final BinanceProperties properties;

    public BinanceTradingDecisionScheduler(BinanceTradingDecisionService decisionService, BinanceProperties properties) {
        this.decisionService = decisionService;
        this.properties = properties;
    }

    /**
     * 每5秒执行一次交易决策
     */
    @Scheduled(fixedDelay = 5000)
    public void analyze() {
        try {
            for (String symbol : properties.getSymbols()) {
                TradingDecision decision = decisionService.analyze(symbol);
                log.info("{} 当前交易决策: {}", symbol, decision.getDecision());
            }
        } catch (Exception e) {
            log.error("Binance Trading Decision 执行异常", e);
        }
    }
}
