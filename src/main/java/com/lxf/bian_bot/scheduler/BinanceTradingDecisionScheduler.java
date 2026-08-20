package com.lxf.bian_bot.scheduler;

import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.service.BinanceTradingDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BinanceTradingDecisionScheduler {

    private @Autowired BinanceTradingDecisionService decisionService;

    /**
     * 每5秒执行一次交易决策
     */
    @Scheduled(fixedDelay = 5000)
    public void analyze() {
        try {
            String symbol = "BTCUSDT";
            TradingDecision decision = decisionService.analyze(symbol);
            log.info("BTCUSDT 当前交易决策: {}", decision.getDecision());
        } catch (Exception e) {
            log.error("Binance Trading Decision 执行异常", e);
        }
    }
}
