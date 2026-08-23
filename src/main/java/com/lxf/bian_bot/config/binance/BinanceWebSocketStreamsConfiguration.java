package com.lxf.bian_bot.config.binance;

import com.lxf.bian_bot.thirdparty.binance.websocket.BinanceWebSocketStreamsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BinanceWebSocketStreamsConfiguration {

    @Bean
    public BinanceWebSocketStreamsClient binanceWebSocketStreamsClient(BinanceProperties properties) {
        BinanceProperties.Streams streams = properties.getStreams();
        return new BinanceWebSocketStreamsClient(
                streams.getWebSocketStreamsUrl(),
                streams.isProxyEnabled(),
                streams.getProxyHost(),
                streams.getProxyPort()
        );
    }
}
