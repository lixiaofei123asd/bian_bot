package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.common.ApiResponse;
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
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.SymbolOrderBookTickerResponse;
import com.lxf.bian_bot.model.ExchangeInfo;
import com.lxf.bian_bot.model.LeverageInfo;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.OrderType;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.model.TimeInForce;
import com.lxf.bian_bot.model.TradingMarginType;
import com.lxf.bian_bot.service.BinanceTradeService;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceTradeServiceImpl implements BinanceTradeService {

    public static final String algoType = "CONDITIONAL";
    public static final String closePosition = "true";
    private final DerivativesTradingUsdsFuturesRestApi api;

    public BinanceTradeServiceImpl(DerivativesTradingUsdsFuturesRestApi api) {
        this.api = api;
    }

    @Override
    public ExchangeInfo getExchangeInfo() {
        return null;
    }

    @Override
    public String getMarginType(String symbol) {
        ApiResponse<PositionInformationV2Response> response = api.positionInformationV2(symbol, null);
        PositionInformationV2Response data = response.getData();
        return data.getFirst().getMarginType();
    }

    @Override
    public boolean setMarginType(String symbol, TradingMarginType tradingMarginType) {
        ChangeMarginTypeRequest changeMarginTypeRequest = new ChangeMarginTypeRequest();
        changeMarginTypeRequest.symbol(symbol);
        MarginType type = MarginType.valueOf(tradingMarginType.name());
        changeMarginTypeRequest.marginType(type);
        ApiResponse<ChangeMarginTypeResponse> response = api.changeMarginType(changeMarginTypeRequest);
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
        double quantity = calculateQuantity(symbol, side, usdtAmount);
        log.info("========== 市价下单 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", side);
        log.info("USDT金额：{}", usdtAmount);
        log.info("实际数量：{}", quantity);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.type(OrderType.MARKET.name());
        request.symbol(symbol);
        request.side(side);
        request.setQuantity(quantity);
        request.setNewOrderRespType(NewOrderRespType.RESULT);

        return newOrder(request, "下单失败！");
    }

    @Override
    public OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce, double usdtAmount, double price) {
        double quantity = calculateQuantity(symbol, side, usdtAmount);
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
        com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce binanceTimeInForce =
                com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce.valueOf(timeInForce.name());
        request.setTimeInForce(binanceTimeInForce);
        request.setQuantity(quantity);
        request.setPrice(price);
        request.setNewOrderRespType(NewOrderRespType.RESULT);

        return newOrder(request, "限价下单失败！");
    }

    @Override
    public OrderInfo createLimitOrderAndTPSL(
            String symbol,
            Side side,
            TimeInForce timeInForce,
            double quantity,
            double price,
            double tp,
            double sl) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("下单数量必须大于 0：" + quantity);
        }
        if (price <= 0) {
            throw new IllegalArgumentException("下单价格必须大于 0：" + price);
        }
        if (tp <= 0 || sl <= 0) {
            throw new IllegalArgumentException("止盈止损价格必须大于 0：tp=" + tp + ", sl=" + sl);
        }

        // 先创建限价开仓单。
        // 注意：限价单如果没有成交，不能提前挂 closePosition=true 的 TP/SL，
        // 否则 TP/SL 可能在未来意外作用于其它持仓。因此只有确认开仓单已经 FILLED 后才创建保护单。
        NewOrderRequest entryRequest = new NewOrderRequest();
        entryRequest.type(OrderType.LIMIT.name());
        entryRequest.symbol(symbol);
        entryRequest.side(side);
        entryRequest.setTimeInForce(
                com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce.valueOf(timeInForce.name()));
        entryRequest.setQuantity(quantity);
        entryRequest.setPrice(price);
        entryRequest.setNewOrderRespType(NewOrderRespType.RESULT);

        log.info("========== 限价开仓 + 止盈止损 ==========");
        log.info("交易对：{}", symbol);
        log.info("开仓方向：{}", side);
        log.info("数量：{}", quantity);
        log.info("开仓价格：{}", price);
        log.info("止盈价格：{}", tp);
        log.info("止损价格：{}", sl);

        ApiResponse<NewOrderResponse> entryResponse = api.newOrder(entryRequest);
        if (entryResponse.getStatusCode() != 200 || entryResponse.getData() == null) {
            throw new RuntimeException("限价开仓失败！");
        }

        NewOrderResponse entryData = entryResponse.getData();
        OrderInfo entryOrder = toOrderInfo(entryData);

        log.info("限价开仓订单已提交: orderId={}, status={}, executedQty={}, avgPrice={}",
                entryData.getOrderId(),
                entryData.getStatus(),
                entryData.getExecutedQty(),
                entryData.getAvgPrice());

        if (!"FILLED".equalsIgnoreCase(entryData.getStatus())) {
            log.info("限价开仓尚未成交，不提前创建止盈止损: symbol={}, orderId={}, status={}",
                    symbol, entryData.getOrderId(), entryData.getStatus());
            return entryOrder;
        }

        // Binance 的条件单使用反向方向平仓。
        Side closeSide = side == Side.BUY ? Side.SELL : Side.BUY;

        // 先挂止损。这样即使后面的止盈创建失败，持仓至少还有止损保护。
        createClosePositionAlgoOrder(
                symbol,
                closeSide,
                OrderType.STOP_MARKET,
                sl,
                "SL");

        createClosePositionAlgoOrder(
                symbol,
                closeSide,
                OrderType.TAKE_PROFIT_MARKET,
                tp,
                "TP");

        log.info("止盈止损设置完成: symbol={}, tp={}, sl={}", symbol, tp, sl);
        log.info("=========================================");
        return entryOrder;
    }

    private void createClosePositionAlgoOrder(
            String symbol,
            Side side,
            OrderType type,
            double triggerPrice,
            String name) {

        NewAlgoOrderRequest request = new NewAlgoOrderRequest();
        request.setAlgoType(algoType);
        request.setSymbol(symbol);
        request.setSide(side);
        request.setType(type.name());
        request.setTriggerPrice(triggerPrice);
        request.setClosePosition(closePosition);
        request.setNewOrderRespType(NewOrderRespType.RESULT);

        ApiResponse<NewAlgoOrderResponse> response = api.newAlgoOrder(request);
        if (response.getStatusCode() != 200 || response.getData() == null) {
            throw new RuntimeException(name + " 止盈止损订单创建失败！symbol=" + symbol + ", triggerPrice=" + triggerPrice);
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

    @Override
    public OrderInfo closeMarketOrder(String symbol) {
        PositionInfo positionInfo = this.getPositionInfo(symbol);
        double positionAmt = positionInfo.getPositionAmt();
        Side closeSide;
        String sideStr = "";
        if (positionAmt > 0) {
            closeSide = Side.SELL;
            sideStr = "平多";
        } else {
            closeSide = Side.BUY;
            sideStr = "平空";
        }

        double quantity = Math.abs(positionAmt);

        log.info("========== 市价平仓 ==========");
        log.info("交易对：{}", symbol);
        log.info("方向：{}", sideStr);
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

    @Override
    public PositionInfo getPosition(String symbol) {
        return getPositionInfo(symbol);
    }

    private PositionInfo getPositionInfo(String symbol) {
        ApiResponse<PositionInformationV2Response> response = api.positionInformationV2(symbol, null);
        PositionInformationV2ResponseInner v2Response = response.getData().getFirst();
        return PositionInfo.builder()
                .symbol(v2Response.getSymbol())
                .positionAmt(Double.parseDouble(v2Response.getPositionAmt()))
                .entryPrice(Double.parseDouble(v2Response.getEntryPrice()))
                .breakEvenPrice(Double.parseDouble(v2Response.getBreakEvenPrice()))
                .markPrice(Double.parseDouble(v2Response.getMarkPrice()))
                .notional(Double.parseDouble(v2Response.getNotional()))
                .unrealizedProfit(Double.parseDouble(v2Response.getUnRealizedProfit()))
                .leverage(Integer.parseInt(v2Response.getLeverage()))
                .marginType(v2Response.getMarginType())
                .positionSide(v2Response.getPositionSide())
                .liquidationPrice(Double.parseDouble(v2Response.getLiquidationPrice()))
                .autoAddMargin(Boolean.parseBoolean(v2Response.getIsAutoAddMargin()))
                .build();
    }

    @Override
    public List<PositionInfo> getAllPositions() {
        return List.of();
    }

    @Override
    public OrderInfo getOrder(String symbol, long orderId) {
        return null;
    }

    @Override
    public OrderInfo cancelOrder(String symbol, long orderId) {
        return null;
    }

    private double calculateQuantity(String symbol, Side side, double usdtAmount) {
        ApiResponse<SymbolOrderBookTickerResponse> bookTicker = api.symbolOrderBookTicker(symbol);
        SymbolOrderBookTickerResponse bookTickerData = bookTicker.getData();

        double price;
        if (Side.BUY.equals(side)) {
            price = Double.parseDouble(Objects.requireNonNull(bookTickerData.getSymbolOrderBookTickerResponse1().getAskPrice()));
        } else {
            price = Double.parseDouble(Objects.requireNonNull(bookTickerData.getSymbolOrderBookTickerResponse1().getBidPrice()));
        }

        if (price <= 0) {
            throw new IllegalArgumentException("盘口价格无效：" + price);
        }

        double quantity = usdtAmount / price;
        DecimalFormat df = new DecimalFormat("#.#");
        return Double.parseDouble(df.format(quantity));
    }

    private OrderInfo newOrder(NewOrderRequest request, String message) {
        ApiResponse<NewOrderResponse> response = api.newOrder(request);
        int statusCode = response.getStatusCode();
        if (200 != statusCode || response.getData() == null) {
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

    private double parseDouble(String value) {
        return value == null || value.isBlank() ? 0D : Double.parseDouble(value);
    }
}
