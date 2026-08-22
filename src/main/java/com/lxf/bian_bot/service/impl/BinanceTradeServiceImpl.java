package com.lxf.bian_bot.service.impl;

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
import com.lxf.bian_bot.model.LeverageInfo;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.OrderType;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.model.TimeInForce;
import com.lxf.bian_bot.model.TradingMarginType;
import com.lxf.bian_bot.service.BinanceTradeService;
import com.lxf.bian_bot.trading.order.OrderParameterCalculator;
import com.lxf.bian_bot.trading.service.PendingOrderProtectionService;
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
    public OrderInfo createMarketOrder(String symbol, Side side, double usdtAmount) {
        ApiResponse<SymbolOrderBookTickerResponse> bookTicker = api.symbolOrderBookTicker(symbol);
        SymbolOrderBookTickerResponse bookTickerData = bookTicker.getData();
        double price = Side.BUY.equals(side)
                ? Double.parseDouble(Objects.requireNonNull(bookTickerData.getSymbolOrderBookTickerResponse1().getAskPrice()))
                : Double.parseDouble(Objects.requireNonNull(bookTickerData.getSymbolOrderBookTickerResponse1().getBidPrice()));
        double quantity = orderParameterCalculator.calculateMarketQuantity(symbol, usdtAmount, price);

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
        request.setQuantity(quantity);
        return newOrder(request, "下单失败！");
    }

    @Override
    public OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce, double usdtAmount, double price) {
        price = orderParameterCalculator.normalizeEntryPrice(symbol, price, Side.BUY.equals(side));
        double quantity = orderParameterCalculator.calculateLimitQuantity(symbol, usdtAmount, price);

        log.info("========== 限价下单 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", side);
        log.info("USDT金额：{}", usdtAmount);
        log.info("单价：{}", price);
        log.info("生效时间：{}", timeInForce.name());
        log.info("实际数量：{}", quantity);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.type(OrderType.LIMIT.name());
        request.symbol(symbol);
        request.side(side);
        request.setTimeInForce(com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce.valueOf(timeInForce.name()));
        request.setQuantity(quantity);
        request.setPrice(price);
        request.setNewOrderRespType(NewOrderRespType.RESULT);
        return newOrder(request, "限价下单失败！");
    }

    @Override
    public OrderInfo createLimitOrderAndTPSLForPrice(String symbol, Side side, TimeInForce timeInForce,
                                                       double quantity, double price, double tp, double sl) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("下单数量必须大于 0：" + quantity);
        }
        if (price <= 0) {
            throw new IllegalArgumentException("下单价格必须大于 0：" + price);
        }
        if (tp <= 0 || sl <= 0) {
            throw new IllegalArgumentException("止盈止损价格必须大于 0：tp=" + tp + ", sl=" + sl);
        }

        price = orderParameterCalculator.normalizeEntryPrice(symbol, price, Side.BUY.equals(side));
        quantity = orderParameterCalculator.normalizeLimitQuantity(symbol, quantity, price);
        tp = orderParameterCalculator.normalizeTriggerPrice(symbol, tp, side, true);
        sl = orderParameterCalculator.normalizeTriggerPrice(symbol, sl, side, false);

        NewOrderRequest entryRequest = new NewOrderRequest();
        entryRequest.type(OrderType.LIMIT.name());
        entryRequest.symbol(symbol);
        entryRequest.side(side);
        entryRequest.setTimeInForce(com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce.valueOf(timeInForce.name()));
        entryRequest.setQuantity(quantity);
        entryRequest.setPrice(price);
        entryRequest.setNewOrderRespType(NewOrderRespType.RESULT);

        log.info("========== 限价开仓 + 止盈止损 ==========");
        log.info("交易对：{}", symbol);
        log.info("开仓方向：{}", side);
        log.info("数量：{}", quantity);
        log.info("开仓价格：{}", price);
        log.info("预计算止盈价格：{}", tp);
        log.info("预计算止损价格：{}", sl);

        ApiResponse<NewOrderResponse> entryResponse = api.newOrder(entryRequest);
        if (entryResponse.getStatusCode() != 200 || entryResponse.getData() == null) {
            throw new RuntimeException("限价开仓失败！");
        }

        NewOrderResponse entryData = entryResponse.getData();
        OrderInfo entryOrder = toOrderInfo(entryData);

        log.info("限价开仓订单已提交: orderId={}, status={}, executedQty={}, avgPrice={}",
                entryData.getOrderId(), entryData.getStatus(), entryData.getExecutedQty(), entryData.getAvgPrice());

        double profitAmount = Side.BUY.equals(side)
                ? (tp - price) * quantity
                : (price - tp) * quantity;
        double lossAmount = Side.BUY.equals(side)
                ? (price - sl) * quantity
                : (sl - price) * quantity;

        pendingOrderProtectionService.register(symbol, entryData.getOrderId(), side, profitAmount, lossAmount);

        log.info("限价开仓保护任务已等待成交: symbol={}, orderId={}, status={}, profitAmount={}, lossAmount={}",
                symbol, entryData.getOrderId(), entryData.getStatus(), profitAmount, lossAmount);
        log.info("=========================================");
        return entryOrder;
    }

    @Override
    public OrderInfo createLimitOrderAndTPSLForProfitAndLoss(String symbol, Side side, TimeInForce timeInForce,
                                                               double quantity, double price, double profitAmount,
                                                               double lossAmount) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("下单数量必须大于 0：" + quantity);
        }
        if (profitAmount <= 0 || lossAmount <= 0) {
            throw new IllegalArgumentException("盈利和亏损金额必须大于 0：profitAmount=" + profitAmount + ", lossAmount=" + lossAmount);
        }

        price = orderParameterCalculator.normalizeEntryPrice(symbol, price, Side.BUY.equals(side));
        quantity = orderParameterCalculator.normalizeLimitQuantity(symbol, quantity, price);

        double tp;
        double sl;
        if (side == Side.BUY) {
            tp = price + profitAmount / quantity;
            sl = price - lossAmount / quantity;
        } else {
            tp = price - profitAmount / quantity;
            sl = price + lossAmount / quantity;
        }

        log.info("按照实际下单价格和数量计算止盈止损: symbol={}, price={}, quantity={}, profitAmount={}, lossAmount={}, tp={}, sl={}",
                symbol, price, quantity, profitAmount, lossAmount, tp, sl);

        return createLimitOrderAndTPSLForPrice(symbol, side, timeInForce, quantity, price, tp, sl);
    }

    @Override
    public OrderInfo createLimitOrderAndTPSLForPercentage(String symbol, Side side, TimeInForce timeInForce,
                                                           double quantity, double price, double profitPercentage,
                                                           double lossPercentage) {
        if (price <= 0) {
            throw new IllegalArgumentException("开仓价格必须大于 0：" + price);
        }
        if (profitPercentage <= 0 || lossPercentage <= 0) {
            throw new IllegalArgumentException("盈利和亏损百分比必须大于 0：profitPercentage=" + profitPercentage + ", lossPercentage=" + lossPercentage);
        }

        price = orderParameterCalculator.normalizeEntryPrice(symbol, price, Side.BUY.equals(side));
        quantity = orderParameterCalculator.normalizeLimitQuantity(symbol, quantity, price);

        double tp;
        double sl;
        if (side == Side.BUY) {
            tp = price * (1 + profitPercentage / 100.0);
            sl = price * (1 - lossPercentage / 100.0);
        } else {
            tp = price * (1 - profitPercentage / 100.0);
            sl = price * (1 + lossPercentage / 100.0);
        }

        log.info("按照盈亏百分比计算止盈止损: symbol={}, profitPercentage={}%, lossPercentage={}%, tp={}, sl={}",
                symbol, profitPercentage, lossPercentage, tp, sl);

        return createLimitOrderAndTPSLForPrice(symbol, side, timeInForce, quantity, price, tp, sl);
    }

    @Override
    public OrderInfo closeMarketOrder(String symbol) {
        PositionInfo positionInfo = this.getPositionInfo(symbol);
        double positionAmt = positionInfo.getPositionAmt();
        Side closeSide = positionAmt > 0 ? Side.SELL : Side.BUY;
        double quantity = Math.abs(positionAmt);
        quantity = orderParameterCalculator.normalizeMarketQuantity(symbol, quantity, positionInfo.getMarkPrice());

        log.info("========== 市价平仓 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", closeSide);
        log.info("实际数量：{}", quantity);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.setSymbol(symbol);
        request.setSide(closeSide);
        request.setType(OrderType.MARKET.name());
        request.setQuantity(quantity);
        request.setReduceOnly("true");
        request.setNewOrderRespType(NewOrderRespType.RESULT);
        return newOrder(request, "市价平仓失败！");
    }

    private PositionInfo getPositionInfo(String symbol) {
        ApiResponse<PositionInformationV2Response> response = api.positionInformationV2(symbol, null);
        PositionInformationV2ResponseInner data = response.getData().getFirst();
        return PositionInfo.builder()
                .symbol(data.getSymbol())
                .positionAmt(Double.parseDouble(data.getPositionAmt()))
                .entryPrice(Double.parseDouble(data.getEntryPrice()))
                .breakEvenPrice(Double.parseDouble(data.getBreakEvenPrice()))
                .markPrice(Double.parseDouble(data.getMarkPrice()))
                .notional(Double.parseDouble(data.getNotional()))
                .unrealizedProfit(Double.parseDouble(data.getUnRealizedProfit()))
                .leverage(Integer.parseInt(data.getLeverage()))
                .marginType(data.getMarginType())
                .positionSide(data.getPositionSide())
                .liquidationPrice(Double.parseDouble(data.getLiquidationPrice()))
                .autoAddMargin(Boolean.parseBoolean(data.getIsAutoAddMargin()))
                .build();
    }

    private double parseDouble(String value) {
        return value == null || value.isBlank() ? 0D : Double.parseDouble(value);
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
