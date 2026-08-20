package com.lxf.bian_bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "binance.streams")
public class BinanceWebSocketProperties {

    private boolean enabled;
    private String host;
    private String port;
    private String webSocketStreamsUrl;
}
