package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorSecondKlineTrend;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.config.strategy.StrategyConfig;
import com.lxf.bian_bot.config.trading.TradingConfig;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceWebSocketStreamsService;
import com.lxf.bian_bot.trading.service.PendingOrderProtectionService;
import com.lxf.bian_bot.trading.state.TradingState;
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
            BinanceTradeService tradeService,
            TradingState tradingState,
            TradingConfig tradingConfig,
            SymbolRuleCache symbolRuleCache,
            PendingOrderProtectionService pendingOrderProtectionService) {
        super(strategyConfig, webSocketStreamsService, tickerCache, tradeService,
                tradingState, tradingConfig, symbolRuleCache, pendingOrderProtectionService);
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
