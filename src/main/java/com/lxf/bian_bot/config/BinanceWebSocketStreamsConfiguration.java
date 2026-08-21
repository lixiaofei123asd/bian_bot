package com.lxf.bian_bot.config;

import com.lxf.bian_bot.websocket.BinanceWebSocketStreamsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BinanceWebSocketStreamsConfiguration {

    @Bean
    public BinanceWebSocketStreamsClient binanceWebSocketStreamsClient(BinanceWebSocketProperties properties) {
        return new BinanceWebSocketStreamsClient(
                properties.getWebSocketStreamsUrl(),
                properties.isProxyEnabled(),
                properties.getProxyHost(),
                properties.getProxyPort()
        );
    }
}
