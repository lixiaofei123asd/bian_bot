package com.lxf.bian_bot.trading.service;

import com.binance.connector.client.common.ApiException;
import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewAlgoOrderRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewAlgoOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRespType;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.analysis.ProtectionTriggerEvent;
import com.lxf.bian_bot.thirdparty.binance.enums.OrderType;
import com.lxf.bian_bot.trading.order.OrderParameterCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 仓位级保护服务。
 *
 * <p>负责成交后的 TP/SL 价格计算、交易规则修正以及保护单创建。
 * 不负责监控限价开仓单，也不负责决定是否平仓。</p>
 */
@Slf4j
@Service
public class PositionProtectionService {

    private static final String IMMEDIATE_TRIGGER_CODE = "-2021";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int CALCULATION_SCALE = 20;

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final OrderParameterCalculator orderParameterCalculator;
    private final ApplicationEventPublisher eventPublisher;

    public PositionProtectionService(DerivativesTradingUsdsFuturesRestApi api,
                                     OrderParameterCalculator orderParameterCalculator,
                                     ApplicationEventPublisher eventPublisher) {
        this.api = api;
        this.orderParameterCalculator = orderParameterCalculator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 为已经成交的仓位创建 TP/SL。
     * 返回值用于 PendingOrderProtectionService 保存部分成功状态。
     */
    public ProtectionResult createProtectionOrders(String symbol,
                                                   long entryOrderId,
                                                   Side openSide,
                                                   BigDecimal quantity,
                                                   BigDecimal entryPrice,
                                                   BigDecimal profitAmount,
                                                   BigDecimal lossAmount,
                                                   boolean slCreated,
                                                   boolean tpCreated) {
        requirePositive(profitAmount, "止盈金额");
        requirePositive(lossAmount, "止损金额");

        entryPrice = orderParameterCalculator.normalizeEntryPrice(
                symbol, entryPrice, Side.BUY.equals(openSide));
        quantity = orderParameterCalculator.normalizeLimitQuantity(symbol, quantity, entryPrice);
        if (quantity.compareTo(ZERO) <= 0) {
            throw new IllegalStateException("成交数量经过交易规则修正后无效: symbol=" + symbol + ", quantity=" + quantity);
        }

        BigDecimal profitPerUnit = profitAmount.divide(quantity, CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal lossPerUnit = lossAmount.divide(quantity, CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal tp = openSide == Side.BUY
                ? entryPrice.add(profitPerUnit)
                : entryPrice.subtract(profitPerUnit);
        BigDecimal sl = openSide == Side.BUY
                ? entryPrice.subtract(lossPerUnit)
                : entryPrice.add(lossPerUnit);

        tp = orderParameterCalculator.normalizeTriggerPrice(symbol, tp, openSide, true);
        sl = orderParameterCalculator.normalizeTriggerPrice(symbol, sl, openSide, false);
        Side closeSide = openSide == Side.BUY ? Side.SELL : Side.BUY;

        log.info("========== 成交仓位创建保护 ==========");
        log.info("交易对：{}", symbol);
        log.info("开仓方向：{}", openSide);
        log.info("平仓方向：{}", closeSide);
        log.info("实际成交数量：{}", quantity);
        log.info("实际成交价格：{}", entryPrice);
        log.info("止盈金额：{}", profitAmount);
        log.info("止损金额：{}", lossAmount);
        log.info("止盈价格：{}", tp);
        log.info("止损价格：{}", sl);

        try {
            if (!slCreated) {
                slCreated = createProtectionAlgoOrder(symbol, closeSide, OrderType.STOP_MARKET, sl, "SL");
            }
            if (!tpCreated) {
                tpCreated = createProtectionAlgoOrder(symbol, closeSide, OrderType.TAKE_PROFIT_MARKET, tp, "TP");
            }
        } catch (ApiException e) {
            if (isImmediateTriggerException(e)) {
                log.warn("保护单创建返回 -2021：价格已经越过保护触发价，发布 ProtectionTriggerEvent，由策略决定是否平仓: symbol={}, orderId={}, side={}, quantity={}, tp={}, sl={}",
                        symbol, entryOrderId, openSide, quantity, tp, sl);
                eventPublisher.publishEvent(new ProtectionTriggerEvent(
                        symbol, openSide, quantity, tp, sl));
                return new ProtectionResult(slCreated, tpCreated, true);
            }
            throw e;
        }

        return new ProtectionResult(slCreated, tpCreated, false);
    }

    private boolean isImmediateTriggerException(ApiException e) {
        String message = e.getMessage();
        return message != null && message.contains(IMMEDIATE_TRIGGER_CODE);
    }

    private boolean createProtectionAlgoOrder(String symbol,
                                              Side side,
                                              OrderType type,
                                              BigDecimal triggerPrice,
                                              String name) {
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
            throw new RuntimeException(name + " 止盈止损订单创建失败！symbol=" + symbol + ", triggerPrice=" + triggerPrice);
        }

        NewAlgoOrderResponse data = response.getData();
        log.info("{} 仓位级保护单创建成功: symbol={}, algoId={}, type={}, side={}, triggerPrice={}, closePosition={}, status={}",
                name, symbol, data.getAlgoId(), data.getOrderType(), data.getSide(),
                data.getTriggerPrice(), "TRUE", data.getAlgoStatus());
        return true;
    }

    private void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(name + "必须大于 0：" + value);
        }
    }

    public record ProtectionResult(boolean slCreated, boolean tpCreated, boolean immediateTriggered) {
    }
}
