package com.lxf.bian_bot.config.binance;

import com.binance.connector.client.common.websocket.configuration.WebSocketClientConfiguration;
import com.binance.connector.client.common.configuration.SignatureConfiguration;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.api.api.DerivativesTradingUsdsFuturesWebSocketApi;
import com.binance.connector.client.derivatives_trading_usds_futures.websocket.api.DerivativesTradingUsdsFuturesWebSocketApiUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(BinanceProperties.class)
public class BinanceWebSocketConfiguration {

    @Bean
    public DerivativesTradingUsdsFuturesWebSocketApi futuresWebSocketApi(BinanceProperties properties) {

        WebSocketClientConfiguration configuration = DerivativesTradingUsdsFuturesWebSocketApiUtil.getClientConfiguration();
        SignatureConfiguration signatureConfiguration = new SignatureConfiguration();
        signatureConfiguration.setApiKey(properties.getApiKey());
        signatureConfiguration.setSecretKey(properties.getSecretKey());
        configuration.setSignatureConfiguration(signatureConfiguration);
        configuration.setUrl(properties.getWebSocketApiUrl());
        return new DerivativesTradingUsdsFuturesWebSocketApi(configuration);
    }
}
