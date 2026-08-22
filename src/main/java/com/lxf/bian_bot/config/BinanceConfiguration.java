package com.lxf.bian_bot.config;

import com.lxf.bian_bot.config.model.BinanceConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binance 配置转换。 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(BinanceProperties.class)
public class BinanceConfiguration {

    private final BinanceProperties binanceProperties;

    /** 将 application.yaml 中的 Binance 配置转换成业务层使用的 Bean。 */
    @Bean
    public BinanceConfig binanceConfig() {
        BinanceProperties.Streams streams = binanceProperties.getStreams();
        return new BinanceConfig(
                streams.getWebSocketStreamsUrl(),
                streams.isProxyEnabled(),
                streams.getProxyHost(),
                streams.getProxyPort());
    }
}
