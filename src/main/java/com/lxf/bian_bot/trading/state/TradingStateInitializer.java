package com.lxf.bian_bot.trading.state;

import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 程序启动时从 Binance 初始化 TradingState。
 *
 * <p>初始化完成后，运行期间的持仓变化由 Binance User Data Stream 的 ACCOUNT_UPDATE
 * 持续更新 TradingState，不需要 Strategy 反复查询 Binance REST。</p>
 */
@Slf4j
@Component
public class TradingStateInitializer {

    private final BinanceTradeService binanceTradeService;
    private final TradingState tradingState;

    public TradingStateInitializer(BinanceTradeService binanceTradeService,
                                   TradingState tradingState) {
        this.binanceTradeService = binanceTradeService;
        this.tradingState = tradingState;
    }

    @PostConstruct
    public void initialize() {
        try {
            List<PositionInfo> positions = binanceTradeService.getPositions();
            positions.forEach(tradingState::updatePosition);
            positions.forEach(position -> tradingState.refreshExecutionState(position.getSymbol()));

            log.info("Binance 持仓初始化完成: positions={}", positions.size());
            positions.stream()
                    .filter(PositionInfo::hasPosition)
                    .forEach(position -> log.info(
                            "初始化持仓: symbol={}, positionAmt={}, entryPrice={}, positionSide={}",
                            position.getSymbol(),
                            position.getPositionAmt(),
                            position.getEntryPrice(),
                            position.getPositionSide()));
        } catch (Exception e) {
            log.error("初始化 Binance 持仓失败，TradingState 将等待 User Data Stream 更新", e);
        }
    }
}
