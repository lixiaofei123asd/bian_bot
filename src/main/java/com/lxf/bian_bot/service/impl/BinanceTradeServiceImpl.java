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

        // 根据 USDT 金额计算币数量
        double quantity = calculateQuantity(symbol, side, usdtAmount);
        log.info("========== 市价下单 ==========");
        log.info("交易对：" + symbol);
        log.info("方向：" + side);
        log.info("USDT金额：" + usdtAmount);
        log.info("实际数量：" + quantity);
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
        // 根据 USDT 金额计算币数量
        double quantity = calculateQuantity(symbol, side, usdtAmount);
        log.info("========== 限价下单 ==========");
        log.info("交易对：" + symbol);
        log.info("方向：" + side);
        log.info("USDT金额：" + usdtAmount);
        log.info("单价：" + price);
        log.info("生效时间：" + timeInForce.name());
        log.info("实际数量：" + quantity);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.type(OrderType.LIMIT.name());
        request.symbol(symbol);
        request.side(side);
        com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce timeInForce1 = com.binance.connector.client.derivatives_trading_usds_futures.rest.model.TimeInForce.valueOf(timeInForce.name());
        request.setTimeInForce(timeInForce1);
        request.setQuantity(quantity);
        request.setPrice(price);

        return newOrder(request, "限价下单失败！");
    }

    @Override
    public OrderInfo createLimitOrderAndTPSL(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double tp, double sl) {
        return null;
    }

    @Override
    public OrderInfo closeMarketOrder(String symbol) {

        PositionInfo positionInfo = this.getPositionInfo(symbol);
        double positionAmt = positionInfo.getPositionAmt();
        Side closeSide;
        String sideStr = "";
        if (positionAmt > 0) {
            // 多仓 → SELL 平仓
            closeSide = Side.SELL;
            sideStr = "平多";
        } else {
            // 空仓 → BUY 平仓
            closeSide = Side.BUY;
            sideStr = "平空";
        }

        double quantity = Math.abs(positionAmt);

        log.info("========== 市价平仓 ==========");
        log.info("交易对：" + symbol);
        log.info("方向：" + sideStr);
        log.info("实际数量：" + quantity);
        log.info("==============================");

        NewOrderRequest request = new NewOrderRequest();
        request.setSymbol(symbol);
        request.setSide(closeSide);
        request.setType(OrderType.MARKET.name());
        request.setQuantity(quantity);
        // 关键：只允许减少仓位
        request.setReduceOnly("true");
        // 返回完整订单结果
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

    /**
     * USDT金额转换成币的数量
     */
    private double calculateQuantity(String symbol, Side side, double usdtAmount) {

        // 获取当前价格
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

        // USDT金额转换成币数量
        double quantity = usdtAmount / price;
        DecimalFormat df = new DecimalFormat("#.#");
        return Double.parseDouble(df.format(quantity));
    }

    private OrderInfo newOrder(NewOrderRequest request, String message) {
        ApiResponse<NewOrderResponse> response = api.newOrder(request);
        int statusCode = response.getStatusCode();
        if (200 != statusCode) {
            throw new RuntimeException(message);
        }

        NewOrderResponse data = response.getData();
        return OrderInfo.builder()
                .symbol(data.getSymbol())
                .orderId(data.getOrderId())
                .clientOrderId(data.getClientOrderId())
                .side(data.getSide())
                .type(data.getType())
                .build();
    }
}
