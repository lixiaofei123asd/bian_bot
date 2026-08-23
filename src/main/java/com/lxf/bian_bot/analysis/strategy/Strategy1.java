package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorSecondKlineTrend;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.model.StrategyConfig;
import com.lxf.bian_bot.service.BinanceWebSocketStreamsService;
import com.lxf.bian_bot.trading.service.BinanceTradingService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Strategy1 extends BaseStrategy {

    private final IndicatorSecondKlineTrend trend;

    public Strategy1(
            IndicatorSecondKlineTrend trend,
            StrategyConfig strategyConfig,
            BinanceWebSocketStreamsService webSocketStreamsService,
            TickerCache tickerCache,
            BinanceTradingService tradingService) {
        super(strategyConfig, webSocketStreamsService, tickerCache, tradingService);
        this.trend = trend;
    }

    @Override
    public String getName() {
        return "STRATEGY1";
    }

    @Override
    public List<Indicator> getIndicators() {
        return List.of(trend);
    }

    @Override
    public int getRequiredMatchCount() {
        return 1;
    }
}
