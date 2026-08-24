package com.lxf.bian_bot.trading.state;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.CurrentAllAlgoOpenOrdersResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.CurrentAllAlgoOpenOrdersResponseInner;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.model.ProtectionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 程序启动时从 Binance 初始化 TradingState。
 *
 * <p>初始化完成后，运行期间的持仓变化由 Binance User Data Stream 的 ACCOUNT_UPDATE 持续更新，
 * TP/SL 保护单由 ALGO_UPDATE 持续更新，不需要 Strategy 反复查询 Binance REST。</p>
 */
@Slf4j
@Component
public class TradingStateInitializer {

    private final BinanceTradeService binanceTradeService;
    private final DerivativesTradingUsdsFuturesRestApi binanceApi;
    private final TradingState tradingState;

    public TradingStateInitializer(BinanceTradeService binanceTradeService,
                                   DerivativesTradingUsdsFuturesRestApi binanceApi,
                                   TradingState tradingState) {
        this.binanceTradeService = binanceTradeService;
        this.binanceApi = binanceApi;
        this.tradingState = tradingState;
    }

    @PostConstruct
    public void initialize() {
        initializePositions();
        initializeProtections();
    }

    private void initializePositions() {
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

    /**
     * 启动时一次性读取当前所有仓位级条件保护单。
     *
     * <p>当前项目的 TP/SL 使用 Binance Futures Conditional Algo Order，
     * 因此不能只查询普通 openOrders；必须查询 openAlgoOrders。</p>
     */
    private void initializeProtections() {
        try {
            ApiResponse<CurrentAllAlgoOpenOrdersResponse> response =
                    binanceApi.currentAllAlgoOpenOrders("CONDITIONAL", null, null, null);
            CurrentAllAlgoOpenOrdersResponse protections = response.getData();
            if (protections == null || protections.isEmpty()) {
                log.info("Binance TP/SL 保护单初始化完成: protections=0");
                return;
            }

            int count = 0;
            for (CurrentAllAlgoOpenOrdersResponseInner order : protections) {
                if (order == null || order.getSymbol() == null || order.getSymbol().isBlank()) continue;
                if (!"NEW".equalsIgnoreCase(order.getAlgoStatus())
                        && !"WORKING".equalsIgnoreCase(order.getAlgoStatus())) continue;

                double triggerPrice = parseDouble(order.getTriggerPrice());
                if (triggerPrice <= 0D) continue;

                String orderType = order.getOrderType();
                if ("TAKE_PROFIT_MARKET".equalsIgnoreCase(orderType)
                        || "TAKE_PROFIT".equalsIgnoreCase(orderType)) {
                    tradingState.updateTakeProfit(order.getSymbol(), triggerPrice,
                            order.getAlgoId() == null ? 0L : order.getAlgoId());
                    count++;
                } else if ("STOP_MARKET".equalsIgnoreCase(orderType)
                        || "STOP".equalsIgnoreCase(orderType)) {
                    tradingState.updateStopLoss(order.getSymbol(), triggerPrice,
                            order.getAlgoId() == null ? 0L : order.getAlgoId());
                    count++;
                }
            }

            log.info("Binance TP/SL 保护单初始化完成: protections={}", count);
            tradingState.getProtections().values().forEach(protection ->
                    log.info("初始化保护价格: symbol={}, takeProfit={}, stopLoss={}, tpAlgoId={}, slAlgoId={}",
                            protection.getSymbol(),
                            protection.getTakeProfitPrice(),
                            protection.getStopLossPrice(),
                            protection.getTakeProfitAlgoId(),
                            protection.getStopLossAlgoId()));
        } catch (Exception e) {
            log.error("初始化 Binance TP/SL 保护单失败，TradingState 将等待 User Data Stream 更新", e);
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0D;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0D;
        }
    }
}
