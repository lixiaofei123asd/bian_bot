package com.lxf.bian_bot.trading.state;

import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.AccountInfo;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.service.BinanceAccountService;
import com.lxf.bian_bot.service.BinanceTradeService;
import java.util.ArrayList;
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

    // 定时刷新账户、持仓和当前委托状态
    @Scheduled(initialDelay = 1000, fixedDelay = 3000)
    public void refresh() {
        try {
            AccountInfo accountInfo = accountService.getAccountInfo();
            tradingState.updateAccount(accountInfo);

            log.info("========== TradingState 账户状态 ==========");
            log.info("钱包余额: {}", accountInfo.getTotalWalletBalance());
            log.info("可用余额: {}", accountInfo.getAvailableBalance());
            log.info("保证金余额: {}", accountInfo.getTotalMarginBalance());
            log.info("未实现盈亏: {}", accountInfo.getTotalUnrealizedProfit());
            log.info("可提现余额: {}", accountInfo.getMaxWithdrawAmount());
            log.info("账户可交易: {}", accountInfo.isCanTrade());

            List<OrderInfo> allOpenOrders = new ArrayList<>();

            log.info("========== TradingState 持仓状态 ==========");
            for (String symbol : properties.getSymbols()) {
                PositionInfo positionInfo = tradeService.getPosition(symbol);
                tradingState.updatePosition(positionInfo);

                if (positionInfo != null && Double.compare(positionInfo.getPositionAmt(), 0D) == 0) {
                    log.info("{}: 当前无持仓", symbol);
                } else if (positionInfo != null) {
                    log.info("{}: positionAmt={}, entryPrice={}, markPrice={}, unrealizedProfit={}, leverage={}, marginType={}",
                            symbol,
                            positionInfo.getPositionAmt(),
                            positionInfo.getEntryPrice(),
                            positionInfo.getMarkPrice(),
                            positionInfo.getUnrealizedProfit(),
                            positionInfo.getLeverage(),
                            positionInfo.getMarginType());
                }

                List<OrderInfo> openOrders = tradeService.getOpenOrders(symbol);
                allOpenOrders.addAll(openOrders);

                log.info("========== {} 当前委托: {} =========={}", symbol, openOrders.size());
                for (OrderInfo order : openOrders) {
                    log.info("orderId={}, side={}, type={}, status={}, origQty={}, executedQty={}, price={}, reduceOnly={}",
                            order.getOrderId(),
                            order.getSide(),
                            order.getType(),
                            order.getStatus(),
                            order.getOrigQty(),
                            order.getExecutedQty(),
                            order.getPrice(),
                            order.isReduceOnly());
                }
            }

            tradingState.replaceOrders(allOpenOrders);
            updateExecutionStates();

            log.info("============================================");
        } catch (Exception e) {
            log.error("TradingState 刷新失败", e);
        }
    }

    // 主动刷新指定币种的账户、持仓和当前委托状态
    public void refreshSymbol(String symbol) {
        AccountInfo accountInfo = accountService.getAccountInfo();
        tradingState.updateAccount(accountInfo);

        PositionInfo positionInfo = tradeService.getPosition(symbol);
        tradingState.updatePosition(positionInfo);

        List<OrderInfo> openOrders = tradeService.getOpenOrders(symbol);
        tradingState.replaceOrders(openOrders);
        updateExecutionState(symbol, positionInfo, openOrders);
    }

    public TradingState getState() {
        return tradingState;
    }

    private void updateExecutionStates() {
        for (String symbol : properties.getSymbols()) {
            PositionInfo positionInfo = tradingState.getPosition(symbol);
            List<OrderInfo> openOrders = tradingState.getOpenOrders(symbol);
            updateExecutionState(symbol, positionInfo, openOrders);
        }
    }

    private void updateExecutionState(String symbol, PositionInfo positionInfo, List<OrderInfo> openOrders) {
        if (positionInfo != null && Double.compare(positionInfo.getPositionAmt(), 0D) != 0) {
            tradingState.updateExecutionState(symbol, TradingExecutionState.POSITION_OPEN);
            return;
        }

        if (openOrders != null && !openOrders.isEmpty()) {
            tradingState.updateExecutionState(symbol, TradingExecutionState.ORDER_OPEN);
            return;
        }

        if (!tradingState.isOrderSubmitting(symbol)) {
            tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
        }
    }
}
