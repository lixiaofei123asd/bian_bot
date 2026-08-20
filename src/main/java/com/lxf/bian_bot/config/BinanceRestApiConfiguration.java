package com.lxf.bian_bot.config;

import com.binance.connector.client.common.configuration.ClientConfiguration;
import com.binance.connector.client.common.configuration.SignatureConfiguration;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.DerivativesTradingUsdsFuturesRestApiUtil;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BinanceProperties.class)
public class BinanceRestApiConfiguration {

    /**
     * 初始化 Binance USDT-M Futures REST API
     * <p>
     * Spring 启动时创建一次。
     * 后续整个程序复用这个实例。
     */
    @Bean
    public DerivativesTradingUsdsFuturesRestApi futuresApi(BinanceProperties properties) {

        System.out.println("========================================");
        System.out.println(" Binance Futures API 初始化");
        System.out.println("========================================");

        ClientConfiguration clientConfiguration = DerivativesTradingUsdsFuturesRestApiUtil.getClientConfiguration();

        SignatureConfiguration signatureConfiguration = new SignatureConfiguration();
        signatureConfiguration.setApiKey(properties.getApiKey());
        signatureConfiguration.setSecretKey(properties.getSecretKey());
        clientConfiguration.setSignatureConfiguration(signatureConfiguration);
        clientConfiguration.setUrl(properties.getBaseUrl());

        System.out.println("环境：" + properties.getBaseUrl());
        System.out.println("========================================");
        DerivativesTradingUsdsFuturesRestApi api = new DerivativesTradingUsdsFuturesRestApi(clientConfiguration);
        System.out.println("Futures REST API 初始化成功");
        System.out.println("========================================");
        return api;
    }

}
