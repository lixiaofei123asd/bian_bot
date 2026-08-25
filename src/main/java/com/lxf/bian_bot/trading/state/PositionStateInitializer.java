package com.lxf.bian_bot.trading.state;

import com.lxf.bian_bot.config.binance.BinanceProperties;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 程序启动时从 Binance 恢复真实持仓到 TradingState。
 *
 * <p>TradingState 是本地状态缓存，Binance 才是持仓最终事实来源。
 * 因此程序启动时必须先恢复交易所已经存在的仓位，避免策略启动后重复开仓。</p>
 */
@Slf4j
@Component
public class PositionStateInitializer {

    private final BinanceProperties properties;
    private final SymbolRuleCache symbolRuleCache;
    private final BinanceTradeService tradeService;
    private final TradingState tradingState;

    public PositionStateInitializer(BinanceProperties properties,
                                    SymbolRuleCache symbolRuleCache,
                                    BinanceTradeService tradeService,
                                    TradingState tradingState) {
        this.properties = properties;
        this.symbolRuleCache = symbolRuleCache;
        this.tradeService = tradeService;
        this.tradingState = tradingState;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        List<String> symbols = symbolRuleCache.filterValidSymbols(properties.getSymbols());
        if (symbols.isEmpty()) {
            log.warn("没有有效的 Binance 交易对，跳过持仓启动恢复");
            return;
        }

        log.info("开始恢复 Binance 持仓状态: symbols={}", symbols);
        for (String symbol : symbols) {
            try {
                initializeSymbol(symbol);
            } catch (Exception e) {
                log.error("Binance 持仓启动恢复异常: symbol={}", symbol, e);
            }
        }
        log.info("Binance 持仓状态启动恢复完成");
    }

    private void initializeSymbol(String symbol) {
        PositionInfo position = tradeService.getPositionInfo(symbol);
        if (position == null) {
            log.warn("Binance 持仓查询结果为空: symbol={}", symbol);
            return;
        }

        tradingState.updatePosition(position);
        tradingState.refreshExecutionState(symbol);

        BigDecimal positionAmt = position.getPositionAmt();
        if (position.hasPosition()) {
            log.info("Binance 已有持仓，已恢复到 TradingState: symbol={}, positionAmt={}, entryPrice={}, positionSide={}, unrealizedProfit={}",
                    symbol,
                    positionAmt,
                    position.getEntryPrice(),
                    position.getPositionSide(),
                    position.getUnrealizedProfit());
        } else {
            log.info("Binance 当前无持仓: symbol={}, positionAmt={}", symbol, positionAmt);
        }
    }
}
