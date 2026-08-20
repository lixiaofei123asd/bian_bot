package com.lxf.bian_bot.config;

import com.binance.connector.client.common.websocket.configuration.WebSocketClientConfiguration;
import com.binance.connector.client.common.configuration.SignatureConfiguration;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.api.api.DerivativesTradingUsdsFuturesWebSocketApi;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.api.DerivativesTradingUsdsFuturesWebSocketApiUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BinanceProperties.class)
public class BinanceWebSocketConfiguration {

    /**
     * 初始化 Binance Futures WebSocket API
     */
    @Bean
    public DerivativesTradingUsdsFuturesWebSocketApi futuresWebSocketApi(BinanceProperties properties) {

        System.out.println("========================================");
        System.out.println(" Binance Futures WebSocket API 初始化");
        System.out.println("========================================");
        WebSocketClientConfiguration configuration = DerivativesTradingUsdsFuturesWebSocketApiUtil.getClientConfiguration();
        SignatureConfiguration signatureConfiguration = new SignatureConfiguration();
        signatureConfiguration.setApiKey(properties.getApiKey());
        signatureConfiguration.setSecretKey(properties.getSecretKey());
        configuration.setSignatureConfiguration(signatureConfiguration);
        configuration.setUrl(properties.getWebSocketApiUrl());

        System.out.println("环境：" + (properties.isTestnet() ? "TESTNET" : "LIVE"));
        System.out.println("WebSocket API URL：" + configuration.getUrl());
        System.out.println("========================================");

        DerivativesTradingUsdsFuturesWebSocketApi api = new DerivativesTradingUsdsFuturesWebSocketApi(configuration);
        System.out.println("Futures WebSocket API 初始化成功");
        System.out.println("========================================");
        return api;
    }
}
