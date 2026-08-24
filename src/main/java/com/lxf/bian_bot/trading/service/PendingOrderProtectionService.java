package com.lxf.bian_bot.trading.service;

import com.binance.connector.client.common.ApiException;
import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewAlgoOrderRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewAlgoOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRespType;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.QueryOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.thirdparty.binance.enums.OrderType;
import com.lxf.bian_bot.trading.order.OrderParameterCalculator;
import com.lxf.bian_bot.trading.state.TradingState;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 监控限价开仓单，并在产生成交后创建仓位级 TP/SL 保护单。 */
@Slf4j
@Service
public class PendingOrderProtectionService {

    private static final String IMMEDIATE_TRIGGER_CODE = "-2021";
    private static final long ENTRY_ORDER_TIMEOUT_MILLIS = 60_000L;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final OrderParameterCalculator orderParameterCalculator;
    private final TradingState tradingState;
    private final Map<Long, PendingProtection> pendingOrders = new ConcurrentHashMap<>();

    public PendingOrderProtectionService(DerivativesTradingUsdsFuturesRestApi api,
                                         OrderParameterCalculator orderParameterCalculator,
                                         TradingState tradingState) {
        this.api = api;
        this.orderParameterCalculator = orderParameterCalculator;
        this.tradingState = tradingState;
    }

    public void register(String symbol, long orderId, Side side,
                         BigDecimal profitAmount, BigDecimal lossAmount) {
        requirePositive(profitAmount, "止盈金额");
        requirePositive(lossAmount, "止损金额");

        pendingOrders.put(orderId, new PendingProtection(
                symbol, orderId, side, profitAmount, lossAmount, System.currentTimeMillis()));
        log.info("限价开仓保护任务已登记: symbol={}, orderId={}, side={}, profitAmount={}, lossAmount={}, timeout={}s",
                symbol, orderId, side, profitAmount, lossAmount, ENTRY_ORDER_TIMEOUT_MILLIS / 1000);
    }

    @Scheduled(fixedDelay = 1000)
    public void checkPendingOrders() {
        if (pendingOrders.isEmpty()) {
            return;
        }
        for (PendingProtection pending : pendingOrders.values()) {
            try {
                process(pending);
            } catch (Exception e) {
                log.error("检查限价开仓保护任务失败: symbol={}, orderId={}", pending.symbol, pending.orderId, e);
            }
        }
    }

    private void process(PendingProtection pending) {
        ApiResponse<QueryOrderResponse> response = api.queryOrder(pending.symbol, pending.orderId, null, null);
        QueryOrderResponse order = response.getData();
        if (order == null || order.getStatus() == null) {
            return;
        }

        String status = order.getStatus();
        BigDecimal executedQty = parseBigDecimal(order.getExecutedQty());

        if (executedQty.compareTo(ZERO) > 0) {
            if ("PARTIALLY_FILLED".equals(status) || "FILLED".equals(status)
                    || "CANCELED".equals(status) || "EXPIRED".equals(status)
                    || "EXPIRED_IN_MATCH".equals(status)) {
                handleFilledQuantity(pending, order, status, executedQty);
            }
            return;
        }

        if ("CANCELED".equals(status) || "EXPIRED".equals(status)
                || "EXPIRED_IN_MATCH".equals(status) || "REJECTED".equals(status)) {
            pendingOrders.remove(pending.orderId);
            tradingState.removeOrder(pending.orderId);
            tradingState.refreshExecutionState(pending.symbol);
            log.info("限价开仓未产生成交，结束保护任务: symbol={}, orderId={}, status={}",
                    pending.symbol, pending.orderId, status);
            return;
        }

        if ("NEW".equals(status) && System.currentTimeMillis() - pending.registerTime >= ENTRY_ORDER_TIMEOUT_MILLIS) {
            cancelExpiredEntryOrder(pending);
        }
    }

    private void handleFilledQuantity(PendingProtection pending, QueryOrderResponse order,
                                      String status, BigDecimal executedQty) {
        BigDecimal avgPrice = parseBigDecimal(order.getAvgPrice());
        if (avgPrice.compareTo(ZERO) <= 0) {
            avgPrice = parseBigDecimal(order.getPrice());
        }
        if (avgPrice.compareTo(ZERO) <= 0) {
            log.warn("开仓已有成交但无法获得有效成交价格，等待下一次检查: symbol={}, orderId={}, status={}",
                    pending.symbol, pending.orderId, status);
            return;
        }

        createProtectionOrders(pending, executedQty, avgPrice);
        if (pending.slCreated && pending.tpCreated) {
            pendingOrders.remove(pending.orderId);
            tradingState.removeOrder(pending.orderId);
            tradingState.refreshExecutionState(pending.symbol);
        }
    }

    private void cancelExpiredEntryOrder(PendingProtection pending) {
        log.info("限价开仓超过{}秒仍未成交，准备取消: symbol={}, orderId={}",
                ENTRY_ORDER_TIMEOUT_MILLIS / 1000, pending.symbol, pending.orderId);
        try {
            api.cancelOrder(pending.symbol, pending.orderId, null, null);
            log.info("限价开仓取消请求已发送: symbol={}, orderId={}", pending.symbol, pending.orderId);
        } catch (Exception e) {
            log.error("限价开仓取消失败，下一次检查继续处理: symbol={}, orderId={}", pending.symbol, pending.orderId, e);
            return;
        }

        ApiResponse<QueryOrderResponse> finalResponse = api.queryOrder(pending.symbol, pending.orderId, null, null);
        QueryOrderResponse finalOrder = finalResponse.getData();
        if (finalOrder == null) {
            return;
        }

        BigDecimal executedQty = parseBigDecimal(finalOrder.getExecutedQty());
        if (executedQty.compareTo(ZERO) > 0) {
            BigDecimal avgPrice = parseBigDecimal(finalOrder.getAvgPrice());
            if (avgPrice.compareTo(ZERO) <= 0) {
                avgPrice = parseBigDecimal(finalOrder.getPrice());
            }
            if (avgPrice.compareTo(ZERO) > 0) {
                log.info("取消挂单时发现订单已经产生成交，转为保护已成交仓位: symbol={}, orderId={}, status={}, executedQty={}, avgPrice={}",
                        pending.symbol, pending.orderId, finalOrder.getStatus(), executedQty, avgPrice);
                createProtectionOrders(pending, executedQty, avgPrice);
                if (pending.slCreated && pending.tpCreated) {
                    pendingOrders.remove(pending.orderId);
                    tradingState.removeOrder(pending.orderId);
                    tradingState.refreshExecutionState(pending.symbol);
                }
            }
            return;
        }

        String finalStatus = finalOrder.getStatus();
        if ("CANCELED".equals(finalStatus) || "EXPIRED".equals(finalStatus)
                || "EXPIRED_IN_MATCH".equals(finalStatus)) {
            pendingOrders.remove(pending.orderId);
            tradingState.removeOrder(pending.orderId);
            tradingState.refreshExecutionState(pending.symbol);
            log.info("限价开仓已超时取消且无成交: symbol={}, orderId={}, status={}",
                    pending.symbol, pending.orderId, finalStatus);
        }
    }

    private void createProtectionOrders(PendingProtection pending,
                                        BigDecimal quantity,
                                        BigDecimal entryPrice) {
        String symbol = pending.symbol;
        Side openSide = pending.side;

        entryPrice = orderParameterCalculator.normalizeEntryPrice(
                symbol, entryPrice, Side.BUY.equals(openSide));
        quantity = orderParameterCalculator.normalizeLimitQuantity(
                symbol, quantity, entryPrice);
        if (quantity.compareTo(ZERO) <= 0) {
            throw new IllegalStateException("成交数量经过交易规则修正后无效: symbol=" + symbol + ", quantity=" + quantity);
        }

        BigDecimal tp;
        BigDecimal sl;
        if (openSide == Side.BUY) {
            tp = entryPrice.add(pending.profitAmount.divide(quantity));
            sl = entryPrice.subtract(pending.lossAmount.divide(quantity));
        } else {
            tp = entryPrice.subtract(pending.profitAmount.divide(quantity));
            sl = entryPrice.add(pending.lossAmount.divide(quantity));
        }

        tp = orderParameterCalculator.normalizeTriggerPrice(symbol, tp, openSide, true);
        sl = orderParameterCalculator.normalizeTriggerPrice(symbol, sl, openSide, false);

        Side closeSide = openSide == Side.BUY ? Side.SELL : Side.BUY;

        log.info("========== 开仓已产生成交，创建仓位级止盈止损 ==========");
        log.info("交易对：{}", symbol);
        log.info("开仓方向：{}", openSide);
        log.info("平仓方向：{}", closeSide);
        log.info("实际成交数量：{}", quantity);
        log.info("实际成交价格：{}", entryPrice);
        log.info("止盈价格：{}", tp);
        log.info("止损价格：{}", sl);

        try {
            if (!pending.slCreated) {
                pending.slCreated = createProtectionAlgoOrder(
                        symbol, closeSide, OrderType.STOP_MARKET, sl, "SL");
            }
            if (!pending.tpCreated) {
                pending.tpCreated = createProtectionAlgoOrder(
                        symbol, closeSide, OrderType.TAKE_PROFIT_MARKET, tp, "TP");
            }
        } catch (ApiException e) {
            if (isImmediateTriggerException(e)) {
                closeImmediatelyAfterProtectionTrigger(pending, openSide, quantity, tp, sl);
                return;
            }
            throw e;
        }

        log.info("止盈止损保护状态: symbol={}, orderId={}, SL={}, TP={}",
                symbol, pending.orderId, pending.slCreated, pending.tpCreated);
        log.info("============================================");
    }

    /**
     * TP/SL 创建时如果 Binance 判断订单会立即触发，则说明当前价格已经越过保护价格。
     * 此时直接使用当前类持有的 Binance API 创建 reduceOnly 市价平仓单。
     */
    private void closeImmediatelyAfterProtectionTrigger(PendingProtection pending,
                                                         Side openSide,
                                                         BigDecimal quantity,
                                                         BigDecimal tp,
                                                         BigDecimal sl) {
        log.warn("当前价格已经越过止盈/止损价格，直接市价平仓: symbol={}, orderId={}, side={}, tp={}, sl={}",
                pending.symbol, pending.orderId, openSide, tp, sl);

        try {
            Side closeSide = openSide == Side.BUY ? Side.SELL : Side.BUY;
            quantity = orderParameterCalculator.normalizeMarketQuantity(
                    pending.symbol, quantity, ZERO);
            if (quantity.compareTo(ZERO) <= 0) {
                log.error("立即市价平仓数量无效，保留保护任务继续处理: symbol={}, orderId={}, quantity={}",
                        pending.symbol, pending.orderId, quantity);
                return;
            }

            NewOrderRequest request = new NewOrderRequest();
            request.setSymbol(pending.symbol);
            request.setSide(closeSide);
            request.setType(OrderType.MARKET.name());
            request.setQuantity(quantity.doubleValue());
            request.setReduceOnly("true");
            request.setNewOrderRespType(NewOrderRespType.RESULT);

            ApiResponse<com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderResponse> response = api.newOrder(request);
            if (response.getStatusCode() != 200 || response.getData() == null) {
                throw new RuntimeException("立即市价平仓失败！symbol=" + pending.symbol);
            }

            var closeOrder = response.getData();
            log.warn("已发送立即市价平仓: symbol={}, orderId={}, closeOrderId={}, status={}, executedQty={}, avgPrice={}",
                    pending.symbol, pending.orderId, closeOrder.getOrderId(), closeOrder.getStatus(),
                    closeOrder.getExecutedQty(), closeOrder.getAvgPrice());

            pendingOrders.remove(pending.orderId);
            tradingState.removeOrder(pending.orderId);
            tradingState.refreshExecutionState(pending.symbol);
        } catch (Exception closeException) {
            log.error("立即市价平仓失败，保留保护任务继续处理: symbol={}, orderId={}",
                    pending.symbol, pending.orderId, closeException);
        }
    }

    private boolean isImmediateTriggerException(ApiException e) {
        String message = e.getMessage();
        return message != null && message.contains(IMMEDIATE_TRIGGER_CODE);
    }

    /**
     * 创建仓位级条件保护单。
     *
     * closePosition=true 表示触发后关闭当前方向的全部仓位，而不是只平固定数量。
     * 因此这里不再设置 quantity，也不设置 reduceOnly。
     */
    private boolean createProtectionAlgoOrder(String symbol, Side side, OrderType type,
                                              BigDecimal triggerPrice, String name) {
        NewAlgoOrderRequest request = new NewAlgoOrderRequest();
        request.setAlgoType("CONDITIONAL");
        request.setSymbol(symbol);
        request.setSide(side);
        request.setType(type.name());
        request.setTriggerPrice(triggerPrice.doubleValue());
        request.setClosePosition("TRUE");
        request.setNewOrderRespType(NewOrderRespType.RESULT);

        ApiResponse<NewAlgoOrderResponse> response = api.newAlgoOrder(request);
        if (response.getStatusCode() != 200 || response.getData() == null) {
            throw new RuntimeException(name + " 止盈止损订单创建失败！symbol=" + symbol
                    + ", triggerPrice=" + triggerPrice);
        }

        NewAlgoOrderResponse data = response.getData();
        log.info("{} 仓位级保护单创建成功: symbol={}, algoId={}, type={}, side={}, triggerPrice={}, closePosition={}, status={}",
                name, symbol, data.getAlgoId(), data.getOrderType(), data.getSide(),
                data.getTriggerPrice(), "TRUE", data.getAlgoStatus());
        return true;
    }

    private BigDecimal parseBigDecimal(String value) {
        return value == null || value.isBlank() ? ZERO : new BigDecimal(value);
    }

    private void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(name + "必须大于 0：" + value);
        }
    }

    private static final class PendingProtection {
        private final String symbol;
        private final long orderId;
        private final Side side;
        private final BigDecimal profitAmount;
        private final BigDecimal lossAmount;
        private final long registerTime;
        private volatile boolean slCreated;
        private volatile boolean tpCreated;

        private PendingProtection(String symbol, long orderId, Side side,
                                  BigDecimal profitAmount, BigDecimal lossAmount, long registerTime) {
            this.symbol = symbol;
            this.orderId = orderId;
            this.side = side;
            this.profitAmount = profitAmount;
            this.lossAmount = lossAmount;
            this.registerTime = registerTime;
        }
    }
}
