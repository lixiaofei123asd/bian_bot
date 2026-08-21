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
 * 负责监控限价开仓单。只有开仓单真正产生成交后，才创建对应的 TP/SL 保护单。
 */
@Slf4j
@Service
public class PendingOrderProtectionService {

    private static final String ALGO_TYPE = "CONDITIONAL";
    private static final String CLOSE_POSITION = "true";

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final OrderParameterCalculator orderParameterCalculator;
    private final Map<Long, PendingProtection> pendingOrders = new ConcurrentHashMap<>();

    public PendingOrderProtectionService(DerivativesTradingUsdsFuturesRestApi api,
                                         OrderParameterCalculator orderParameterCalculator) {
        this.api = api;
        this.orderParameterCalculator = orderParameterCalculator;
    }

    public void register(String symbol, long orderId, Side side, double profitAmount, double lossAmount) {
        pendingOrders.put(orderId, new PendingProtection(symbol, orderId, side, profitAmount, lossAmount));
        log.info("限价开仓保护任务已登记: symbol={}, orderId={}, side={}, profitAmount={}, lossAmount={}",
                symbol, orderId, side, profitAmount, lossAmount);
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
                log.error("检查限价开仓保护任务失败: symbol={}, orderId={}", pending.symbol(), pending.orderId(), e);
            }
        }
    }

    private void process(PendingProtection pending) {
        ApiResponse<QueryOrderResponse> response = api.queryOrder(
                pending.symbol(), pending.orderId(), null, null);
        QueryOrderResponse order = response.getData();
        if (order == null || order.getStatus() == null) {
            return;
        }

        String status = order.getStatus();
        double executedQty = parseDouble(order.getExecutedQty());

        if ("NEW".equals(status) || "PARTIALLY_FILLED".equals(status)) {
            return;
        }

        if (executedQty <= 0) {
            if ("CANCELED".equals(status) || "EXPIRED".equals(status)
                    || "EXPIRED_IN_MATCH".equals(status) || "REJECTED".equals(status)) {
                pendingOrders.remove(pending.orderId());
                log.info("限价开仓未产生成交，取消保护任务: symbol={}, orderId={}, status={}",
                        pending.symbol(), pending.orderId(), status);
            }
            return;
        }

        if (!"FILLED".equals(status) && !"CANCELED".equals(status) && !"EXPIRED".equals(status)
                && !"EXPIRED_IN_MATCH".equals(status)) {
            return;
        }

        double avgPrice = parseDouble(order.getAvgPrice());
        if (avgPrice <= 0) {
            String price = order.getPrice();
            avgPrice = parseDouble(price);
        }
        if (avgPrice <= 0) {
            log.warn("开仓已成交但无法获得有效成交价格，等待下一次检查: symbol={}, orderId={}, status={}",
                    pending.symbol(), pending.orderId(), status);
            return;
        }

        createProtectionOrders(pending, executedQty, avgPrice);
        pendingOrders.remove(pending.orderId());
    }

    private void createProtectionOrders(PendingProtection pending, double quantity, double entryPrice) {
        String symbol = pending.symbol();
        Side side = pending.side();

        entryPrice = orderParameterCalculator.normalizeEntryPrice(symbol, entryPrice, Side.BUY.equals(side));
        quantity = orderParameterCalculator.normalizeLimitQuantity(symbol, quantity, entryPrice);

        double tp;
        double sl;
        if (side == Side.BUY) {
            tp = entryPrice + pending.profitAmount() / quantity;
            sl = entryPrice - pending.lossAmount() / quantity;
        } else {
            tp = entryPrice - pending.profitAmount() / quantity;
            sl = entryPrice + pending.lossAmount() / quantity;
        }

        tp = orderParameterCalculator.normalizeTriggerPrice(symbol, tp, side, true);
        sl = orderParameterCalculator.normalizeTriggerPrice(symbol, sl, side, false);

        Side closeSide = side == Side.BUY ? Side.SELL : Side.BUY;

        log.info("========== 开仓成交，创建止盈止损 ==========");
        log.info("交易对：{}", symbol);
        log.info("开仓方向：{}", side);
        log.info("实际成交数量：{}", quantity);
        log.info("实际成交价格：{}", entryPrice);
        log.info("止盈价格：{}", tp);
        log.info("止损价格：{}", sl);

        createClosePositionAlgoOrder(symbol, closeSide, OrderType.STOP_MARKET, sl, "SL");
        createClosePositionAlgoOrder(symbol, closeSide, OrderType.TAKE_PROFIT_MARKET, tp, "TP");

        log.info("止盈止损保护完成: symbol={}, entryPrice={}, quantity={}, tp={}, sl={}",
                symbol, entryPrice, quantity, tp, sl);
        log.info("============================================");
    }

    private void createClosePositionAlgoOrder(String symbol, Side side, OrderType type,
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
    }

    private double parseDouble(String value) {
        return value == null || value.isBlank() ? 0D : Double.parseDouble(value);
    }

    private record PendingProtection(String symbol, long orderId, Side side,
                                     double profitAmount, double lossAmount) {
    }
}
