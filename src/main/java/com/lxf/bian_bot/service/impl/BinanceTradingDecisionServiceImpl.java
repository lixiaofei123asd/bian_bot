package com.lxf.bian_bot.service.impl;

import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.service.BinanceMarketAnalysisService;
import com.lxf.bian_bot.service.BinanceTradingDecisionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceTradingDecisionServiceImpl implements BinanceTradingDecisionService {

    private final TickerCache tickerCache;
    private final BinanceMarketAnalysisService marketAnalysisService;

    public BinanceTradingDecisionServiceImpl(TickerCache tickerCache, BinanceMarketAnalysisService marketAnalysisService) {
        this.tickerCache = tickerCache;
        this.marketAnalysisService = marketAnalysisService;
    }

    @Override
    public TradingDecision analyze(String symbol) {
        log.info("========== 开始交易决策分析: {} ==========", symbol);

        // 1. 获取最新行情
        BinanceBookTicker ticker = tickerCache.get(symbol);
        if (ticker == null) {
            log.warn("{} 暂无最新行情", symbol);
            return waitDecision(symbol, "暂无最新行情");
        }

        BigDecimal bid = ticker.getBidPrice();
        BigDecimal ask = ticker.getAskPrice();
        BigDecimal currentPrice = bid.add(ask).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);

        //2. 获取市场分析结果
        BinanceMarketAnalysis analysis = marketAnalysisService.analyze(symbol);

        if (analysis == null) {
            log.warn("{} 市场分析结果为空", symbol);
            return waitDecision(symbol, "市场分析结果为空");
        }

        //3. 根据策略结果判断方向
        boolean longSignal = checkLongSignal(analysis);
        boolean shortSignal = checkShortSignal(analysis);

        //4. 判断 LONG
        if (longSignal) {
            BigDecimal entry = ask;
            BigDecimal stopLoss = entry.multiply(BigDecimal.valueOf(0.9985));
            BigDecimal takeProfit = entry.multiply(BigDecimal.valueOf(1.003));
            BigDecimal risk = entry.subtract(stopLoss).abs();
            BigDecimal reward = takeProfit.subtract(entry).abs();
            BigDecimal ratio = reward.divide(risk, 4, RoundingMode.HALF_UP);

            TradingDecision decision = TradingDecision.builder()
                    .symbol(symbol)
                    .decision(TradingDecision.DecisionType.ENTER_LONG)
                    .direction(TradingDecision.Direction.LONG)
                    .currentPrice(currentPrice)
                    .entryPrice(entry)
                    .stopLossPrice(stopLoss)
                    .takeProfitPrice(takeProfit)
                    .risk(risk)
                    .reward(reward)
                    .riskRewardRatio(ratio)
                    .reason("LONG策略条件满足")
                    .build();
            printDecision(decision);
            return decision;
        }

        /*
         * =========================================================
         * 5. 判断 SHORT
         * =========================================================
         */
        if (shortSignal) {
            BigDecimal entry = bid;
            BigDecimal stopLoss = entry.multiply(BigDecimal.valueOf(1.0015));
            BigDecimal takeProfit = entry.multiply(BigDecimal.valueOf(0.997));
            BigDecimal risk = entry.subtract(stopLoss).abs();
            BigDecimal reward = entry.subtract(takeProfit).abs();
            BigDecimal ratio = reward.divide(risk, 4, RoundingMode.HALF_UP);

            TradingDecision decision = TradingDecision.builder()
                    .symbol(symbol)
                    .decision(TradingDecision.DecisionType.ENTER_SHORT)
                    .direction(TradingDecision.Direction.SHORT)
                    .currentPrice(currentPrice)
                    .entryPrice(entry)
                    .stopLossPrice(stopLoss)
                    .takeProfitPrice(takeProfit)
                    .risk(risk)
                    .reward(reward)
                    .riskRewardRatio(ratio)
                    .reason("SHORT策略条件满足")
                    .build();
            printDecision(decision);
            return decision;
        }

        /*
         * =========================================================
         * 6. 没有交易信号
         * =========================================================
         */
        return waitDecision(symbol, "当前没有满足入场条件");
    }

    /**
     * 判断做多条件
     * <p>
     * 第一版先留接口。
     * <p>
     * 后面直接接你的 IndicatorEngine / Strategy Result。
     */
    private boolean checkLongSignal(BinanceMarketAnalysis analysis) {
        return false;
    }

    /**
     * 判断做空条件
     */
    private boolean checkShortSignal(BinanceMarketAnalysis analysis) {
        return false;
    }

    /**
     * WAIT
     */
    private TradingDecision waitDecision(String symbol, String reason) {
        TradingDecision decision = TradingDecision.builder()
                .symbol(symbol)
                .decision(TradingDecision.DecisionType.WAIT)
                .reason(reason)
                .build();
        log.info("交易决策: {}", decision);
        return decision;
    }

    /**
     * 输出交易决策
     */
    private void printDecision(TradingDecision decision) {
        log.info("""
                
                ========================================
                交易决策
                ========================================
                Symbol       : {}
                Decision     : {}
                Direction    : {}
                Current      : {}
                Entry        : {}
                Stop Loss    : {}
                Take Profit  : {}
                Risk         : {}
                Reward       : {}
                R/R          : {}
                Reason       : {}
                ========================================
                """, decision.getSymbol(), decision.getDecision(), decision.getDirection(), decision.getCurrentPrice(), decision.getEntryPrice(), decision.getStopLossPrice(), decision.getTakeProfitPrice(), decision.getRisk(), decision.getReward(), decision.getRiskRewardRatio(), decision.getReason());
    }
}
