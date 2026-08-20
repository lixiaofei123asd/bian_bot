package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.common.ApiException;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.api.api.DerivativesTradingUsdsFuturesWebSocketApi;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.api.model.SymbolPriceTickerRequest;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.api.model.SymbolPriceTickerResponse;
import com.lxf.bian_bot.service.BinanceWebSocketService;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceWebSocketServiceImpl implements BinanceWebSocketService {

    private final DerivativesTradingUsdsFuturesWebSocketApi webSocketApi;

    public BinanceWebSocketServiceImpl(DerivativesTradingUsdsFuturesWebSocketApi webSocketApi) {
        this.webSocketApi = webSocketApi;
    }

    /**
     * 查询单个交易对最新价格
     */
    public CompletableFuture<SymbolPriceTickerResponse> getPrice(String symbol) {
        SymbolPriceTickerRequest request = new SymbolPriceTickerRequest().symbol(symbol);
        return webSocketApi.symbolPriceTicker(request);
    }

    /**
     * 测试 BTCUSDT
     */
    @Override
    public void testPrice() {
        try {
            SymbolPriceTickerRequest request = new SymbolPriceTickerRequest().symbol("BTCUSDT");
            System.out.println("================================");
            System.out.println("开始调用 Binance WebSocket API");
            System.out.println("symbol = BTCUSDT");
            System.out.println("================================");

            CompletableFuture<SymbolPriceTickerResponse> future = webSocketApi.symbolPriceTicker(request);

            System.out.println("请求已经发送，等待 Binance 返回...");

            SymbolPriceTickerResponse response = future.join();

            System.out.println("================================");
            System.out.println("Binance WebSocket API 返回");
            System.out.println(response);
            System.out.println("================================");

        } catch (ApiException e) {
            log.error("调用 Binance WebSocket API 失败", e);
        }
    }

    /**
     * 停止 WebSocket
     */
    public void stop() {

        try {
            webSocketApi.stop();
            log.info("Binance WebSocket API 已停止");
        } catch (Exception e) {
            log.error("停止 Binance WebSocket API 失败", e);
        }
    }
}
