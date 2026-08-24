package com.lxf.bian_bot.thirdparty.binance.service.impl;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ChangeInitialLeverageRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ChangeInitialLeverageResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ChangeMarginTypeRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ChangeMarginTypeResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.MarginType;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderRespType;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NewOrderResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PositionInformationV2Response;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.PositionInformationV2ResponseInner;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.SymbolOrderBookTickerResponse;
import com.lxf.bian_bot.thirdparty.binance.enums.OrderType;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.enums.TradingMarginType;
import com.lxf.bian_bot.thirdparty.binance.model.LeverageInfo;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import com.lxf.bian_bot.trading.order.OrderParameterCalculator;
import com.lxf.bian_bot.trading.service.PendingOrderProtectionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceTradeServiceImpl implements BinanceTradeService {

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final OrderParameterCalculator orderParameterCalculator;
    private final PendingOrderProtectionService pendingOrderProtectionService;

    public BinanceTradeServiceImpl(DerivativesTradingUsdsFuturesRestApi api,
                                   OrderParameterCalculator orderParameterCalculator,
                                   PendingOrderProtectionService pendingOrderProtectionService) {
        this.api = api;
        this.orderParameterCalculator = orderParameterCalculator;
        this.pendingOrderProtectionService = pendingOrderProtectionService;
    }

    @Override
    public List<PositionInfo> getPositions() {
        ApiResponse<PositionInformationV2Response> response = api.positionInformationV2(null, null);
        if (response.getData() == null || response.getData().isEmpty()) {
            return List.of();
        }
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
                .maxNotionalValue(Double.parseDouble(data.getMaxNotionalValue()))
                .build();
    }

    @Override
    public OrderInfo createMarketOrder(String symbol, Side side, BigDecimal usdtAmount) {
        ApiResponse<SymbolOrderBookTickerResponse> bookTicker = api.symbolOrderBookTicker(symbol);
        SymbolOrderBookTickerResponse bookTickerData = bookTicker.getData();
        BigDecimal price = Side.BUY.equals(side)
                ? new BigDecimal(Objects.requireNonNull(bookTickerData.getSymbolOrderBookTickerResponse1().getAskPrice()))
                : new BigDecimal(Objects.requireNonNull(bookTickerData.getSymbolOrderBookTickerResponse1().getBidPrice()));

        BigDecimal quantity = orderParameterCalculator.calculateMarketQuantity(symbol, usdtAmount, price);

        log.info("========== 市价下单 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", side);
        log.info("USDT金额：{}", usdtAmount);
        log.info("实际数量：{}", quantity);
        log.info("盘口价格：{}", price);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.type(OrderType.MARKET.name());
        request.symbol(symbol);
        request.side(side);
        request.setQuantity(quantity.doubleValue());
        return newOrder(request, "下单失败！");
    }

    @Override
    public OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce,
                                      BigDecimal usdtAmount, BigDecimal price) {
        BigDecimal normalizedPrice = orderParameterCalculator.normalizeEntryPrice(
                symbol, price, Side.BUY.equals(side));
        BigDecimal quantity = orderParameterCalculator.calculateLimitQuantity(
                symbol, usdtAmount, normalizedPrice);

        log.info("========== 限价下单 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", side);
        log.info("AI/策略金额：{}", usdtAmount);
        log.info("修正后单价：{}", normalizedPrice);
        log.info("生效时间：{}", timeInForce.name());
        log.info("修正后数量：{}", quantity);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.type(OrderType.LIMIT.name());
        request.symbol(symbol);
        request.side(side);
        request.setTimeInForce(com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce.valueOf(timeInForce.name()));
        request.setQuantity(quantity.doubleValue());
        request.setPrice(normalizedPrice.doubleValue());
        request.setNewOrderRespType(NewOrderRespType.RESULT);
        return newOrder(request, "限价下单失败！");
    }

    @Override
    public OrderInfo createLimitOrderAndTPSLForPrice(String symbol, Side side, TimeInForce timeInForce,
                                                       BigDecimal quantity, BigDecimal price,
                                                       BigDecimal tp, BigDecimal sl) {
        BigDecimal normalizedPrice = orderParameterCalculator.normalizeEntryPrice(
                symbol, price, Side.BUY.equals(side));
        BigDecimal normalizedQuantity = orderParameterCalculator.normalizeLimitQuantity(
                symbol, quantity, normalizedPrice);
        BigDecimal normalizedTp = orderParameterCalculator.normalizeTriggerPrice(
                symbol, tp, side, true);
        BigDecimal normalizedSl = orderParameterCalculator.normalizeTriggerPrice(
                symbol, sl, side, false);

        NewOrderRequest entryRequest = new NewOrderRequest();
        entryRequest.type(OrderType.LIMIT.name());
        entryRequest.symbol(symbol);
        entryRequest.side(side);
        entryRequest.setTimeInForce(com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce.valueOf(timeInForce.name()));
        entryRequest.setQuantity(normalizedQuantity.doubleValue());
        entryRequest.setPrice(normalizedPrice.doubleValue());
        entryRequest.setNewOrderRespType(NewOrderRespType.RESULT);

        log.info("========== 限价开仓 + 止盈止损 ==========");
        log.info("交易对：{}", symbol);
        log.info("开仓方向：{}", side);
        log.info("原始数量：{} -> 修正数量：{}", quantity, normalizedQuantity);
        log.info("原始开仓价格：{} -> 修正价格：{}", price, normalizedPrice);
        log.info("原始止盈：{} -> 修正止盈：{}", tp, normalizedTp);
        log.info("原始止损：{} -> 修正止损：{}", sl, normalizedSl);

        ApiResponse<NewOrderResponse> entryResponse = api.newOrder(entryRequest);
        if (entryResponse.getStatusCode() != 200 || entryResponse.getData() == null) {
            throw new RuntimeException("限价开仓失败！");
        }

        NewOrderResponse entryData = entryResponse.getData();
        OrderInfo entryOrder = toOrderInfo(entryData);

        log.info("限价开仓订单已提交: orderId={}, status={}, executedQty={}, avgPrice={}",
                entryData.getOrderId(), entryData.getStatus(), entryData.getExecutedQty(), entryData.getAvgPrice());

        BigDecimal profitAmount = Side.BUY.equals(side)
                ? normalizedTp.subtract(normalizedPrice).multiply(normalizedQuantity)
                : normalizedPrice.subtract(normalizedTp).multiply(normalizedQuantity);
        BigDecimal lossAmount = Side.BUY.equals(side)
                ? normalizedPrice.subtract(normalizedSl).multiply(normalizedQuantity)
                : normalizedSl.subtract(normalizedPrice).multiply(normalizedQuantity);

        pendingOrderProtectionService.register(symbol, entryData.getOrderId(), side,
                profitAmount.doubleValue(), lossAmount.doubleValue());

        log.info("限价开仓保护任务已等待成交: symbol={}, orderId={}, status={}, profitAmount={}, lossAmount={}",
                symbol, entryData.getOrderId(), entryData.getStatus(), profitAmount, lossAmount);
        log.info("=========================================");
        return entryOrder;
    }

    @Override
    public OrderInfo createLimitOrderAndTPSLForProfitAndLoss(String symbol, Side side, TimeInForce timeInForce,
                                                               BigDecimal quantity, BigDecimal price,
                                                               BigDecimal profitAmount, BigDecimal lossAmount) {
        if (profitAmount == null || profitAmount.compareTo(BigDecimal.ZERO) <= 0
                || lossAmount == null || lossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("盈利和亏损金额必须大于 0：profitAmount=" + profitAmount + ", lossAmount=" + lossAmount);
        }

        BigDecimal normalizedPrice = orderParameterCalculator.normalizeEntryPrice(
                symbol, price, Side.BUY.equals(side));
        BigDecimal normalizedQuantity = orderParameterCalculator.normalizeLimitQuantity(
                symbol, quantity, normalizedPrice);

        BigDecimal tp;
        BigDecimal sl;
        if (side == Side.BUY) {
            tp = normalizedPrice.add(profitAmount.divide(normalizedQuantity, 20, java.math.RoundingMode.HALF_UP));
            sl = normalizedPrice.subtract(lossAmount.divide(normalizedQuantity, 20, java.math.RoundingMode.HALF_UP));
        } else {
            tp = normalizedPrice.subtract(profitAmount.divide(normalizedQuantity, 20, java.math.RoundingMode.HALF_UP));
            sl = normalizedPrice.add(lossAmount.divide(normalizedQuantity, 20, java.math.RoundingMode.HALF_UP));
        }

        log.info("按照修正后的实际下单价格和数量计算止盈止损: symbol={}, price={}, quantity={}, profitAmount={}, lossAmount={}, tp={}, sl={}",
                symbol, normalizedPrice, normalizedQuantity, profitAmount, lossAmount, tp, sl);

        return createLimitOrderAndTPSLForPrice(symbol, side, timeInForce,
                normalizedQuantity, normalizedPrice, tp, sl);
    }

    @Override
    public OrderInfo createLimitOrderAndTPSLForPercentage(String symbol, Side side, TimeInForce timeInForce,
                                                           BigDecimal quantity, BigDecimal price,
                                                           BigDecimal profitPercentage, BigDecimal lossPercentage) {
        if (profitPercentage == null || profitPercentage.compareTo(BigDecimal.ZERO) <= 0
                || lossPercentage == null || lossPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("盈利和亏损百分比必须大于 0：profitPercentage="
                    + profitPercentage + ", lossPercentage=" + lossPercentage);
        }

        BigDecimal normalizedPrice = orderParameterCalculator.normalizeEntryPrice(
                symbol, price, Side.BUY.equals(side));
        BigDecimal normalizedQuantity = orderParameterCalculator.normalizeLimitQuantity(
                symbol, quantity, normalizedPrice);

        BigDecimal hundred = BigDecimal.valueOf(100);
        BigDecimal tp;
        BigDecimal sl;
        if (side == Side.BUY) {
            tp = normalizedPrice.multiply(BigDecimal.ONE.add(profitPercentage.divide(hundred, 20, java.math.RoundingMode.HALF_UP)));
            sl = normalizedPrice.multiply(BigDecimal.ONE.subtract(lossPercentage.divide(hundred, 20, java.math.RoundingMode.HALF_UP)));
        } else {
            tp = normalizedPrice.multiply(BigDecimal.ONE.subtract(profitPercentage.divide(hundred, 20, java.math.RoundingMode.HALF_UP)));
            sl = normalizedPrice.multiply(BigDecimal.ONE.add(lossPercentage.divide(hundred, 20, java.math.RoundingMode.HALF_UP)));
        }

        log.info("按照盈亏百分比计算止盈止损: symbol={}, profitPercentage={}%, lossPercentage={}%, tp={}, sl={}",
                symbol, profitPercentage, lossPercentage, tp, sl);

        return createLimitOrderAndTPSLForPrice(symbol, side, timeInForce,
                normalizedQuantity, normalizedPrice, tp, sl);
    }

    @Override
    public OrderInfo closeMarketOrder(String symbol) {
        PositionInfo positionInfo = this.getPositionInfo(symbol);
        BigDecimal positionAmt = BigDecimal.valueOf(positionInfo.getPositionAmt());
        if (positionAmt.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("当前没有可平仓位：" + symbol);
        }

        Side closeSide = positionAmt.compareTo(BigDecimal.ZERO) > 0 ? Side.SELL : Side.BUY;
        BigDecimal quantity = orderParameterCalculator.normalizeMarketQuantity(
                symbol, positionAmt.abs(), BigDecimal.ZERO);

        log.info("========== 市价平仓 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", closeSide);
        log.info("实际数量：{}", quantity);
        log.info("==============================");

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
                .positionAmt(parseDouble(data.getPositionAmt()))
                .entryPrice(parseDouble(data.getEntryPrice()))
                .breakEvenPrice(parseDouble(data.getBreakEvenPrice()))
                .markPrice(parseDouble(data.getMarkPrice()))
                .notional(parseDouble(data.getNotional()))
                .unrealizedProfit(parseDouble(data.getUnRealizedProfit()))
                .leverage(parseInt(data.getLeverage()))
                .marginType(data.getMarginType())
                .positionSide(data.getPositionSide())
                .liquidationPrice(parseDouble(data.getLiquidationPrice()))
                .autoAddMargin(Boolean.parseBoolean(data.getIsAutoAddMargin()))
                .build();
    }

    private double parseDouble(String value) {
        return value == null || value.isBlank() ? 0D : Double.parseDouble(value);
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
                .origQty(parseDouble(data.getOrigQty()))
                .executedQty(parseDouble(data.getExecutedQty()))
                .avgPrice(parseDouble(data.getAvgPrice()))
                .price(parseDouble(data.getPrice()))
                .status(data.getStatus())
                .reduceOnly(data.getReduceOnly())
                .positionSide(data.getPositionSide())
                .updateTime(data.getUpdateTime())
                .build();
    }
}
