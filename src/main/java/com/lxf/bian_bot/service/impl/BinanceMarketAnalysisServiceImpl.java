package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.lxf.bian_bot.indicator.IndicatorEngine;
import com.lxf.bian_bot.indicator.IndicatorResultStore;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import com.lxf.bian_bot.service.BinanceMarketAnalysisService;
import com.lxf.bian_bot.service.BinanceMarketService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceMarketAnalysisServiceImpl implements BinanceMarketAnalysisService {

    private final BinanceMarketService marketService;
    private final IndicatorEngine indicatorEngine;
    private final IndicatorResultStore resultStore;

    public BinanceMarketAnalysisServiceImpl(BinanceMarketService marketService, IndicatorEngine indicatorEngine, IndicatorResultStore resultStore) {
        this.marketService = marketService;
        this.indicatorEngine = indicatorEngine;
        this.resultStore = resultStore;
    }

    @Override
    public BinanceMarketAnalysis analyze(String symbol) {
        log.info("========== 开始分析 {} ==========", symbol);

        /* 1. 获取 K 线*/
        List<BinanceKline> klines = marketService.getKlines(symbol, Interval.INTERVAL_1m, 100L);

        if (klines == null || klines.isEmpty()) {
            log.warn("{} K线数据为空", symbol);
            return new BinanceMarketAnalysis();
        }

        /**2. 创建分析结果*/
        BinanceMarketAnalysis analysis = new BinanceMarketAnalysis();

        /**3. 执行指标计算*/
        indicatorEngine.calculate(klines, analysis);

        /**4. 保存到全局结果*/
        resultStore.update(symbol, analysis);

        /**
         * ========================================
         * 4. 获取多空账户比
         * ========================================
         *
         * 这个数据不是每次都必须请求。
         *
         * 后面我们会单独做缓存。
         */
//        List<BinanceLongShortRatio> ratios = marketService.getLongShortRatio(symbol, Period.PERIOD_5m, 10L);
        log.info("========== 分析结束 {} ==========", symbol);
        return analysis;
    }
}
