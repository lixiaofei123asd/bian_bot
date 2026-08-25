package com.lxf.bian_bot.trading.service;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.QueryOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.analysis.OrderFilledEvent;
import com.lxf.bian_bot.trading.state.TradingState;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 待成交限价开仓单监控服务。
 *
 * <p>这里只负责监控订单是否成交、超时取消以及成交后的状态清理。
 * TP/SL 完全由具体 Strategy 决定，本类不保存、不计算也不创建任何 TP/SL。</p>
 */
@Slf4j
@Service
public class PendingOrderProtectionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final long ENTRY_ORDER_TIMEOUT_MILLIS = 60_000L;

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final TradingState tradingState;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<Long, PendingOrder> pendingOrders = new ConcurrentHashMap<>();

    public PendingOrderProtectionService(DerivativesTradingUsdsFuturesRestApi api,
                                         TradingState tradingState,
                                         ApplicationEventPublisher eventPublisher) {
        this.api = api;
        this.tradingState = tradingState;
        this.eventPublisher = eventPublisher;
    }

    /** 登记一个等待成交的限价开仓单。 */
    public void register(String symbol, long orderId, Side side) {
        pendingOrders.put(orderId, new PendingOrder(
                symbol, orderId, side, System.currentTimeMillis()));
        log.info("限价开仓监控任务已登记: symbol={}, orderId={}, side={}, timeout={}s",
                symbol, orderId, side, ENTRY_ORDER_TIMEOUT_MILLIS / 1000);
    }

    @Scheduled(fixedDelay = 1000)
    public void checkPendingOrders() {
        if (pendingOrders.isEmpty()) return;
        for (PendingOrder pending : pendingOrders.values()) {
            try {
                process(pending);
            } catch (Exception e) {
                log.error("检查限价开仓订单失败: symbol={}, orderId={}", pending.symbol, pending.orderId, e);
            }
        }
    }

    private void process(PendingOrder pending) {
        ApiResponse<QueryOrderResponse> response = api.queryOrder(pending.symbol, pending.orderId, null, null);
        QueryOrderResponse order = response.getData();
        if (order == null || order.getStatus() == null) return;

        String status = order.getStatus();
        BigDecimal executedQty = parseBigDecimal(order.getExecutedQty());

        if (executedQty.compareTo(ZERO) > 0) {
            if ("PARTIALLY_FILLED".equals(status) || "FILLED".equals(status)
                    || "CANCELED".equals(status) || "EXPIRED".equals(status)
                    || "EXPIRED_IN_MATCH".equals(status)) {
                publishFilledEvent(pending, order, executedQty);
            }
            return;
        }

        if ("CANCELED".equals(status) || "EXPIRED".equals(status)
                || "EXPIRED_IN_MATCH".equals(status) || "REJECTED".equals(status)) {
            removePendingOrder(pending);
            return;
        }

        if ("NEW".equals(status)
                && System.currentTimeMillis() - pending.registerTime >= ENTRY_ORDER_TIMEOUT_MILLIS) {
            cancelExpiredEntryOrder(pending);
        }
    }

    private void publishFilledEvent(PendingOrder pending,
                                    QueryOrderResponse order,
                                    BigDecimal executedQty) {
        BigDecimal avgPrice = parseBigDecimal(order.getAvgPrice());
        if (avgPrice.compareTo(ZERO) <= 0) avgPrice = parseBigDecimal(order.getPrice());
        if (avgPrice.compareTo(ZERO) <= 0) {
            log.warn("开仓已有成交但无法获得有效成交价格，等待下一次检查: symbol={}, orderId={}, status={}",
                    pending.symbol, pending.orderId, order.getStatus());
            return;
        }

        log.info("限价开仓已成交，发布成交事件: symbol={}, orderId={}, side={}, quantity={}, entryPrice={}",
                pending.symbol, pending.orderId, pending.side, executedQty, avgPrice);

        eventPublisher.publishEvent(new OrderFilledEvent(
                pending.symbol,
                pending.orderId,
                pending.side,
                executedQty,
                avgPrice));

        removePendingOrder(pending);
    }

    private void cancelExpiredEntryOrder(PendingOrder pending) {
        log.info("限价开仓超过{}秒仍未成交，准备取消: symbol={}, orderId={}",
                ENTRY_ORDER_TIMEOUT_MILLIS / 1000, pending.symbol, pending.orderId);
        try {
            api.cancelOrder(pending.symbol, pending.orderId, null, null);
        } catch (Exception e) {
            log.error("限价开仓取消失败，下一次检查继续处理: symbol={}, orderId={}", pending.symbol, pending.orderId, e);
            return;
        }

        ApiResponse<QueryOrderResponse> finalResponse = api.queryOrder(pending.symbol, pending.orderId, null, null);
        QueryOrderResponse finalOrder = finalResponse.getData();
        if (finalOrder == null) return;

        BigDecimal executedQty = parseBigDecimal(finalOrder.getExecutedQty());
        if (executedQty.compareTo(ZERO) > 0) {
            publishFilledEvent(pending, finalOrder, executedQty);
            return;
        }

        String finalStatus = finalOrder.getStatus();
        if ("CANCELED".equals(finalStatus) || "EXPIRED".equals(finalStatus)
                || "EXPIRED_IN_MATCH".equals(finalStatus)) {
            removePendingOrder(pending);
        }
    }

    private void removePendingOrder(PendingOrder pending) {
        pendingOrders.remove(pending.orderId);
        tradingState.removeOrder(pending.orderId);
        tradingState.refreshExecutionState(pending.symbol);
    }

    private BigDecimal parseBigDecimal(String value) {
        return value == null || value.isBlank() ? ZERO : new BigDecimal(value);
    }

    private static final class PendingOrder {
        private final String symbol;
        private final long orderId;
        private final Side side;
        private final long registerTime;

        private PendingOrder(String symbol, long orderId, Side side, long registerTime) {
            this.symbol = symbol;
            this.orderId = orderId;
            this.side = side;
            this.registerTime = registerTime;
        }
    }
}
