package com.lxf.bian_bot.trading.state;

import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.AccountInfo;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.service.BinanceAccountService;
import com.lxf.bian_bot.service.BinanceTradeService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TradingStateManager {

    private final BinanceAccountService accountService;
    private final BinanceTradeService tradeService;
    private final BinanceProperties properties;
    private final TradingState tradingState;

    public TradingStateManager(BinanceAccountService accountService, BinanceTradeService tradeService, BinanceProperties properties, TradingState tradingState) {
        this.accountService = accountService;
        this.tradeService = tradeService;
        this.properties = properties;
        this.tradingState = tradingState;
    }

    // 启动后立即同步一次账户和持仓
    @Scheduled(initialDelay = 1000, fixedDelay = 3000)
    public void refresh() {
        try {
            AccountInfo accountInfo = accountService.getAccountInfo();
            tradingState.updateAccount(accountInfo);

            for (String symbol : properties.getSymbols()) {
                PositionInfo positionInfo = tradeService.getPosition(symbol);
                tradingState.updatePosition(positionInfo);
            }

            log.debug("TradingState 刷新完成: symbols={}", properties.getSymbols());
        } catch (Exception e) {
            log.error("TradingState 刷新失败", e);
        }
    }

    // 主动刷新指定币种的账户和持仓状态
    public void refreshSymbol(String symbol) {
        AccountInfo accountInfo = accountService.getAccountInfo();
        tradingState.updateAccount(accountInfo);

        PositionInfo positionInfo = tradeService.getPosition(symbol);
        tradingState.updatePosition(positionInfo);
    }

    public TradingState getState() {
        return tradingState;
    }
}
