package com.lxf.bian_bot.thirdparty.binance.service.impl;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.common.ApiException;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ChangeInitialLeverageRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ChangeInitialLeverageResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ChangeMarginTypeRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ChangeMarginTypeResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.MarginType;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewAlgoOrderRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewAlgoOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRespType;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PositionInformationV2Response;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PositionInformationV2ResponseInner;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.thirdparty.binance.enums.OrderType;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.enums.TradingMarginType;
import com.lxf.bian_bot.thirdparty.binance.model.LeverageInfo;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceTradeServiceImpl implements BinanceTradeService {

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final com.lxf.bian_bot.trading.order.OrderParameterCalculator orderParameterCalculator;

    public BinanceTradeServiceImpl(DerivativesTradingUsdsFuturesRestApi api,
                                   com.lxf.bian_bot.trading.order.OrderParameterCalculator orderParameterCalculator) {
        this.api = api;
        this.orderParameterCalculator = orderParameterCalculator;
    }

    @Override
    public List<PositionInfo> getPositions() {
        ApiResponse<PositionInformationV2Response> response = api.positionInformationV2(null, null);
        if (response.getData() == null || response.getData().isEmpty()) return List.of();
        return response.getData().stream().map(this::toPositionInfo).toList();
    }

    @Override
    public PositionInfo getPositionInfo(String symbol) {
        ApiResponse<PositionInformationV2Response> response = api.positionInformationV2(symbol, null);
        if (response.getData() == null || response.getData().isEmpty()) {
            return PositionInfo.builder().symbol(symbol).build();
        }
        return toPositionInfo(response.getData().getFirst());
    }

    @Override
    public String getMarginType(String symbol) {
        ApiResponse<PositionInformationV2Response> response = api.positionInformationV2(symbol, null);
        return response.getData().getFirst().getMarginType();
    }

    @Override
    public boolean setMarginType(String symbol, TradingMarginType tradingMarginType) {
        ChangeMarginTypeRequest request = new ChangeMarginTypeRequest();
        request.symbol(symbol);
        request.marginType(MarginType.valueOf(tradingMarginType.name()));
        ApiResponse<ChangeMarginTypeResponse> response = api.changeMarginType(request);
        return "success".equalsIgnoreCase(response.getData().getMsg());
    }

    @Override
    public LeverageInfo setLeverage(String symbol, Long leverage) {
        ChangeInitialLeverageRequest request = new ChangeInitialLeverageRequest();
        request.setSymbol(symbol);
        request.setLeverage(leverage);
        ApiResponse<ChangeInitialLeverageResponse> response = api.changeInitialLeverage(request);
        ChangeInitialLeverageResponse data = response.getData();
        return LeverageInfo.builder()
                .symbol(data.getSymbol())
                .leverage(data.getLeverage().intValue())
                .maxNotionalValue(parseBigDecimal(data.getMaxNotionalValue()))
                .build();
    }

    @Override
    public void createProtectionOrders(String symbol,
                                       Side openSide,
                                       BigDecimal quantity,
                                       BigDecimal takeProfitPrice,
                                       BigDecimal stopLossPrice) {
        BigDecimal normalizedQuantity = orderParameterCalculator.normalizeMarketQuantity(
                symbol, quantity, BigDecimal.ZERO);
        if (normalizedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("保护单数量无效: symbol=" + symbol + ", quantity=" + quantity);
        }

        BigDecimal normalizedTp = orderParameterCalculator.normalizeTriggerPrice(
                symbol, takeProfitPrice, openSide, true);
        BigDecimal normalizedSl = orderParameterCalculator.normalizeTriggerPrice(
                symbol, stopLossPrice, openSide, false);
        Side closeSide = openSide == Side.BUY ? Side.SELL : Side.BUY;

        log.info("========== 创建策略保护单 ==========");
        log.info("交易对：{}", symbol);
        log.info("开仓方向：{}", openSide);
        log.info("平仓方向：{}", closeSide);
        log.info("保护数量：{}", normalizedQuantity);
        log.info("止盈价格：{}", normalizedTp);
        log.info("止损价格：{}", normalizedSl);

        createProtectionAlgoOrder(symbol, closeSide, OrderType.STOP_MARKET, normalizedSl, "SL");
        createProtectionAlgoOrder(symbol, closeSide, OrderType.TAKE_PROFIT_MARKET, normalizedTp, "TP");
    }

    private void createProtectionAlgoOrder(String symbol,
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

        try {
            ApiResponse<NewAlgoOrderResponse> response = api.newAlgoOrder(request);
            if (response.getStatusCode() != 200 || response.getData() == null) {
                throw new RuntimeException(name + " 保护单创建失败！symbol=" + symbol
                        + ", triggerPrice=" + triggerPrice);
            }
            NewAlgoOrderResponse data = response.getData();
            log.info("{} 保护单创建成功: symbol={}, algoId={}, type={}, side={}, triggerPrice={}, status={}",
                    name, symbol, data.getAlgoId(), data.getOrderType(), data.getSide(),
                    data.getTriggerPrice(), data.getAlgoStatus());
        } catch (ApiException e) {
            throw e;
        }
    }

    @Override
    public OrderInfo createMarketOrder(String symbol, Side side, BigDecimal quantity) {
        log.info("========== 市价下单 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", side);
        log.info("实际数量：{}", quantity);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.type(OrderType.MARKET.name());
        request.symbol(symbol);
        request.side(side);
        request.setQuantity(quantity.doubleValue());
        return newOrder(request, "下单失败！");
    }

    @Override
    public OrderInfo createLimitOrder(String symbol,
                                      Side side,
                                      TimeInForce timeInForce,
                                      BigDecimal quantity,
                                      BigDecimal price) {
        log.info("========== 限价下单 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", side);
        log.info("修正后单价：{}", price);
        log.info("生效时间：{}", timeInForce.name());
        log.info("修正后数量：{}", quantity);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.type(OrderType.LIMIT.name());
        request.symbol(symbol);
        request.side(side);
        request.setTimeInForce(
                com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce
                        .valueOf(timeInForce.name()));
        request.setQuantity(quantity.doubleValue());
        request.setPrice(price.doubleValue());
        request.setNewOrderRespType(NewOrderRespType.RESULT);
        return newOrder(request, "限价下单失败！");
    }

    @Override
    public OrderInfo closeMarketOrder(String symbol) {
        PositionInfo positionInfo = getPositionInfo(symbol);
        BigDecimal positionAmt = positionInfo.getPositionAmt();
        if (positionAmt == null || positionAmt.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("当前没有可平仓位：" + symbol);
        }

        Side closeSide = positionAmt.compareTo(BigDecimal.ZERO) > 0 ? Side.SELL : Side.BUY;
        BigDecimal quantity = orderParameterCalculator.normalizeMarketQuantity(
                symbol, positionAmt.abs(), BigDecimal.ZERO);

        NewOrderRequest request = new NewOrderRequest();
        request.setSymbol(symbol);
        request.setSide(closeSide);
        request.setType(OrderType.MARKET.name());
        request.setQuantity(quantity.doubleValue());
        request.setReduceOnly("true");
        request.setNewOrderRespType(NewOrderRespType.RESULT);
        return newOrder(request, "市价平仓失败！");
    }

    private PositionInfo toPositionInfo(PositionInformationV2ResponseInner data) {
        return PositionInfo.builder()
                .symbol(data.getSymbol())
                .positionAmt(parseBigDecimal(data.getPositionAmt()))
                .entryPrice(parseBigDecimal(data.getEntryPrice()))
                .breakEvenPrice(parseBigDecimal(data.getBreakEvenPrice()))
                .markPrice(parseBigDecimal(data.getMarkPrice()))
                .notional(parseBigDecimal(data.getNotional()))
                .unrealizedProfit(parseBigDecimal(data.getUnRealizedProfit()))
                .leverage(parseInt(data.getLeverage()))
                .marginType(data.getMarginType())
                .positionSide(data.getPositionSide())
                .liquidationPrice(parseBigDecimal(data.getLiquidationPrice()))
                .autoAddMargin(Boolean.parseBoolean(data.getIsAutoAddMargin()))
                .build();
    }

    private BigDecimal parseBigDecimal(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private int parseInt(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private OrderInfo newOrder(NewOrderRequest request, String message) {
        ApiResponse<NewOrderResponse> response = api.newOrder(request);
        if (response.getStatusCode() != 200 || response.getData() == null) {
            throw new RuntimeException(message);
        }
        return toOrderInfo(response.getData());
    }

    private OrderInfo toOrderInfo(NewOrderResponse data) {
        return OrderInfo.builder()
                .symbol(data.getSymbol())
                .orderId(data.getOrderId())
                .clientOrderId(data.getClientOrderId())
                .side(data.getSide())
                .type(data.getType())
                .origQty(parseBigDecimal(data.getOrigQty()))
                .executedQty(parseBigDecimal(data.getExecutedQty()))
                .avgPrice(parseBigDecimal(data.getAvgPrice()))
                .price(parseBigDecimal(data.getPrice()))
                .status(data.getStatus())
                .reduceOnly(data.getReduceOnly())
                .positionSide(data.getPositionSide())
                .updateTime(data.getUpdateTime())
                .build();
    }
}
