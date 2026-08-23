package com.lxf.bian_bot.config.binance;

import com.binance.connector.client.common.configuration.ClientConfiguration;
import com.binance.connector.client.common.configuration.SignatureConfiguration;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.DerivativesTradingUsdsFuturesRestApiUtil;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.UserDataStreamsApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
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

        log.info("开始初始化 Binance Futures API");
        log.info("环境：{}", properties.getBaseUrl());
        ClientConfiguration clientConfiguration = createClientConfiguration(properties);
        clientConfiguration.setUrl(properties.getBaseUrl());
        DerivativesTradingUsdsFuturesRestApi api = new DerivativesTradingUsdsFuturesRestApi(clientConfiguration);
        log.info("初始化 Binance Futures API 成功！");
        return api;
    }

    /**
     * Binance Futures User Data Stream REST API。
     * 用于创建、续期和关闭 listenKey。
     */
    @Bean
    public UserDataStreamsApi userDataStreamsApi(BinanceProperties properties) {
        log.info("开始初始化 UserData Streams API");
        log.info("环境：{}", properties.getBaseUrl());
        ClientConfiguration clientConfiguration = createClientConfiguration(properties);
        clientConfiguration.setUrl(properties.getBaseUrl());
        UserDataStreamsApi api = new UserDataStreamsApi(clientConfiguration);
        log.info("初始化 UserData Streams API 成功！");
        return api;
    }

    private ClientConfiguration createClientConfiguration(BinanceProperties properties) {
        ClientConfiguration clientConfiguration = DerivativesTradingUsdsFuturesRestApiUtil.getClientConfiguration();
        SignatureConfiguration signatureConfiguration = new SignatureConfiguration();
        signatureConfiguration.setApiKey(properties.getApiKey());
        signatureConfiguration.setSecretKey(properties.getSecretKey());
        clientConfiguration.setSignatureConfiguration(signatureConfiguration);

        return clientConfiguration;
    }
}
