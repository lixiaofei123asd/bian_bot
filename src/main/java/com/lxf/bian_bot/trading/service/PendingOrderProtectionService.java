package com.lxf.bian_bot.trading.service;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewAlgoOrderRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewAlgoOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRespType;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.QueryOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.model.OrderType;
import com.lxf.bian_bot.trading.order.OrderParameterCalculator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 监控限价开仓单，并在第一次产生成交后创建官方 Futures API 支持的 TP/SL 保护单。
 *
 * <p>这里不调用 Binance 网页的私有 OTOCO BAPI，而是用公开 Futures API 模拟 OTOCO 的核心行为：
 * <p>1. 开仓单 NEW 时不创建保护单；
 * <p>2. PARTIALLY_FILLED 或 FILLED 时立即创建保护单；
 * <p>3. 保护单使用 closePosition=true，因此后续继续成交的仓位也由同一组保护单覆盖；
 * <p>4. 如果 TP/SL 中一个创建失败，下一次检查只重试失败的那一个；
 * <p>5. 开仓单超过配置的挂单时效仍未产生任何成交，则取消剩余限价单。
 */
@Slf4j
@Service
public class PendingOrderProtectionService {

    private static final String ALGO_TYPE = "CONDITIONAL";
    private static final String CLOSE_POSITION = "true";

    /**
     * 限价开仓最大等待时间，单位毫秒。
     *
     * <p>当前按需求固定为 60 秒；后续如果需要做成 application.yml 配置，
     * 可以再接入 TradingConfig，不改变这里的状态机逻辑。
     */
    private static final long ENTRY_ORDER_TIMEOUT_MILLIS = 60_000L;

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final OrderParameterCalculator orderParameterCalculator;
    private final Map<Long, PendingProtection> pendingOrders = new ConcurrentHashMap<>();

    public PendingOrderProtectionService(DerivativesTradingUsdsFuturesRestApi api,
                                         OrderParameterCalculator orderParameterCalculator) {
        this.api = api;
        this.orderParameterCalculator = orderParameterCalculator;
    }

    public void register(String symbol, long orderId, Side side, double profitAmount, double lossAmount) {
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
        ApiResponse<QueryOrderResponse> response = api.queryOrder(
                pending.symbol, pending.orderId, null, null);
        QueryOrderResponse order = response.getData();
        if (order == null || order.getStatus() == null) {
            return;
        }

        String status = order.getStatus();
        double executedQty = parseDouble(order.getExecutedQty());

        // 已经有成交：优先处理成交保护，不能因为超过60秒而取消已经形成的仓位。
        if (executedQty > 0) {
            if ("PARTIALLY_FILLED".equals(status)
                    || "FILLED".equals(status)
                    || "CANCELED".equals(status)
                    || "EXPIRED".equals(status)
                    || "EXPIRED_IN_MATCH".equals(status)) {
                handleFilledQuantity(pending, order, status, executedQty);
            }
            return;
        }

        // 没有任何成交且已经进入终态，保护任务直接结束。
        if ("CANCELED".equals(status) || "EXPIRED".equals(status)
                || "EXPIRED_IN_MATCH".equals(status) || "REJECTED".equals(status)) {
            pendingOrders.remove(pending.orderId);
            log.info("限价开仓未产生成交，结束保护任务: symbol={}, orderId={}, status={}",
                    pending.symbol, pending.orderId, status);
            return;
        }

        // 仍然是 NEW：检查是否已经超过最大挂单时间。
        if ("NEW".equals(status) && System.currentTimeMillis() - pending.registerTime >= ENTRY_ORDER_TIMEOUT_MILLIS) {
            cancelExpiredEntryOrder(pending);
        }
    }

    private void handleFilledQuantity(PendingProtection pending, QueryOrderResponse order,
                                      String status, double executedQty) {
        double avgPrice = parseDouble(order.getAvgPrice());
        if (avgPrice <= 0) {
            avgPrice = parseDouble(order.getPrice());
        }
        if (avgPrice <= 0) {
            log.warn("开仓已有成交但无法获得有效成交价格，等待下一次检查: symbol={}, orderId={}, status={}",
                    pending.symbol, pending.orderId, status);
            return;
        }

        // 关键：PARTIALLY_FILLED 就已经有持仓，可以开始保护，不必等 FILLED。
        createProtectionOrders(pending, executedQty, avgPrice);

        if (pending.slCreated && pending.tpCreated) {
            pendingOrders.remove(pending.orderId);
        }
    }

    /**
     * 取消超过60秒仍完全没有成交的限价开仓单。
     *
     * <p>取消请求后再次查询最终状态，避免刚好在60秒边界发生成交时误认为没有成交。
     * 如果发现竞态下已经产生了成交，则立即按实际成交数量创建保护单，而不是丢弃仓位。
     */
    private void cancelExpiredEntryOrder(PendingProtection pending) {
        log.info("限价开仓超过{}秒仍未成交，准备取消: symbol={}, orderId={}",
                ENTRY_ORDER_TIMEOUT_MILLIS / 1000, pending.symbol, pending.orderId);

        try {
            api.cancelOrder(pending.symbol, pending.orderId, null, null);
            log.info("限价开仓取消请求已发送: symbol={}, orderId={}", pending.symbol, pending.orderId);
        } catch (Exception e) {
            // 取消失败时不删除任务，下一秒继续查询并重试，避免挂单遗留。
            log.error("限价开仓取消失败，下一次检查继续处理: symbol={}, orderId={}",
                    pending.symbol, pending.orderId, e);
            return;
        }

        // 取消成功后重新查询最终状态，处理取消与成交同时发生的竞态。
        ApiResponse<QueryOrderResponse> finalResponse = api.queryOrder(
                pending.symbol, pending.orderId, null, null);
        QueryOrderResponse finalOrder = finalResponse.getData();
        if (finalOrder == null) {
            return;
        }

        double executedQty = parseDouble(finalOrder.getExecutedQty());
        if (executedQty > 0) {
            String finalStatus = finalOrder.getStatus();
            double avgPrice = parseDouble(finalOrder.getAvgPrice());
            if (avgPrice <= 0) {
                avgPrice = parseDouble(finalOrder.getPrice());
            }
            if (avgPrice > 0) {
                log.info("取消挂单时发现订单已经产生成交，转为保护已成交仓位: symbol={}, orderId={}, status={}, executedQty={}, avgPrice={}",
                        pending.symbol, pending.orderId, finalStatus, executedQty, avgPrice);
                createProtectionOrders(pending, executedQty, avgPrice);
                if (pending.slCreated && pending.tpCreated) {
                    pendingOrders.remove(pending.orderId);
                }
            }
            return;
        }

        String finalStatus = finalOrder.getStatus();
        if ("CANCELED".equals(finalStatus) || "EXPIRED".equals(finalStatus)
                || "EXPIRED_IN_MATCH".equals(finalStatus)) {
            pendingOrders.remove(pending.orderId);
            log.info("限价开仓已超时取消且无成交: symbol={}, orderId={}, status={}",
                    pending.symbol, pending.orderId, finalStatus);
        }
    }

    private void createProtectionOrders(PendingProtection pending, double quantity, double entryPrice) {
        String symbol = pending.symbol;
        Side side = pending.side;

        entryPrice = orderParameterCalculator.normalizeEntryPrice(symbol, entryPrice, Side.BUY.equals(side));
        quantity = orderParameterCalculator.normalizeLimitQuantity(symbol, quantity, entryPrice);
        if (quantity <= 0) {
            throw new IllegalStateException("成交数量经过交易规则修正后无效: symbol=" + symbol + ", quantity=" + quantity);
        }

        double tp;
        double sl;
        if (side == Side.BUY) {
            tp = entryPrice + pending.profitAmount / quantity;
            sl = entryPrice - pending.lossAmount / quantity;
        } else {
            tp = entryPrice - pending.profitAmount / quantity;
            sl = entryPrice + pending.lossAmount / quantity;
        }

        tp = orderParameterCalculator.normalizeTriggerPrice(symbol, tp, side, true);
        sl = orderParameterCalculator.normalizeTriggerPrice(symbol, sl, side, false);

        Side closeSide = side == Side.BUY ? Side.SELL : Side.BUY;

        log.info("========== 开仓已产生成交，创建止盈止损 ==========");
        log.info("交易对：{}", symbol);
        log.info("开仓方向：{}", side);
        log.info("实际成交数量：{}", quantity);
        log.info("实际成交价格：{}", entryPrice);
        log.info("止盈价格：{}", tp);
        log.info("止损价格：{}", sl);

        // 使用 closePosition=true，不传 quantity。
        // 这样第一次部分成交后建立的保护单，会覆盖该方向后续继续增加的整个持仓，
        // 更接近网页 OTOCO 的“开仓触发保护”行为。
        if (!pending.slCreated) {
            pending.slCreated = createClosePositionAlgoOrder(
                    symbol, closeSide, OrderType.STOP_MARKET, sl, "SL");
        }

        if (!pending.tpCreated) {
            pending.tpCreated = createClosePositionAlgoOrder(
                    symbol, closeSide, OrderType.TAKE_PROFIT_MARKET, tp, "TP");
        }

        log.info("止盈止损保护状态: symbol={}, orderId={}, SL={}, TP={}",
                symbol, pending.orderId, pending.slCreated, pending.tpCreated);
        log.info("============================================");
    }

    private boolean createClosePositionAlgoOrder(String symbol, Side side, OrderType type,
                                                 double triggerPrice, String name) {
        NewAlgoOrderRequest request = new NewAlgoOrderRequest();
        request.setAlgoType(ALGO_TYPE);
        request.setSymbol(symbol);
        request.setSide(side);
        request.setType(type.name());
        request.setTriggerPrice(triggerPrice);
        request.setClosePosition(CLOSE_POSITION);
        request.setNewOrderRespType(NewOrderRespType.RESULT);

        ApiResponse<NewAlgoOrderResponse> response = api.newAlgoOrder(request);
        if (response.getStatusCode() != 200 || response.getData() == null) {
            throw new RuntimeException(name + " 止盈止损订单创建失败！symbol=" + symbol
                    + ", triggerPrice=" + triggerPrice);
        }

        NewAlgoOrderResponse data = response.getData();
        log.info("{} 保护单创建成功: symbol={}, algoId={}, type={}, side={}, triggerPrice={}, status={}",
                name,
                symbol,
                data.getAlgoId(),
                data.getOrderType(),
                data.getSide(),
                data.getTriggerPrice(),
                data.getAlgoStatus());
        return true;
    }

    private double parseDouble(String value) {
        return value == null || value.isBlank() ? 0D : Double.parseDouble(value);
    }

    private static final class PendingProtection {
        private final String symbol;
        private final long orderId;
        private final Side side;
        private final double profitAmount;
        private final double lossAmount;
        private final long registerTime;
        private volatile boolean slCreated;
        private volatile boolean tpCreated;

        private PendingProtection(String symbol, long orderId, Side side,
                                  double profitAmount, double lossAmount, long registerTime) {
            this.symbol = symbol;
            this.orderId = orderId;
            this.side = side;
            this.profitAmount = profitAmount;
            this.lossAmount = lossAmount;
            this.registerTime = registerTime;
        }
    }
}
