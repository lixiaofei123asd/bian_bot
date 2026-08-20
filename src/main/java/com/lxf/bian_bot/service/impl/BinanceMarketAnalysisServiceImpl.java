package com.lxf.bian_bot.service.impl;

import com.lxf.bian_bot.cache.KlineCache;
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

    private static final String KLINE_INTERVAL = "1m";
    private final BinanceMarketService marketService;
    private final IndicatorEngine indicatorEngine;
    private final IndicatorResultStore resultStore;
    private final KlineCache klineCache;

    public BinanceMarketAnalysisServiceImpl(BinanceMarketService marketService, IndicatorEngine indicatorEngine, IndicatorResultStore resultStore, KlineCache klineCache) {
        this.marketService = marketService;
        this.indicatorEngine = indicatorEngine;
        this.resultStore = resultStore;
        this.klineCache = klineCache;
    }

    @Override
    public BinanceMarketAnalysis analyze(String symbol) {
        log.info("========== 开始分析 {} ==========", symbol);

        /* 1. 获取 K 线*/
        List<BinanceKline> klines = klineCache.get(symbol, KLINE_INTERVAL);
        log.debug("从 KlineCache 获取 K线: symbol={}, interval={}, size={}", symbol, KLINE_INTERVAL, klines.size());

        if (klines.isEmpty()) {
            log.warn("{} KlineCache 中没有 K线数据", symbol);
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
