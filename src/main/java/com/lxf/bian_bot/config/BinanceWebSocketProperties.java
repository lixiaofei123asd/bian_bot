package com.lxf.bian_bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "binance.streams")
public class BinanceWebSocketProperties {

    /**
     * 是否使用代理。
     */
    private boolean proxyEnabled;

    /**
     * 代理主机。
     */
    private String proxyHost;

    /**
     * 代理端口。
     */
    private int proxyPort;

    /**
     * Binance WebSocket Streams 地址。
     */
    private String webSocketStreamsUrl;
}
