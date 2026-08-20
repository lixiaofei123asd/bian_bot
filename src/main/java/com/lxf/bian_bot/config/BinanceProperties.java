package com.lxf.bian_bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "binance")
public class BinanceProperties {

    private String apiKey;
    private String secretKey;
    private boolean testnet;
    private String baseUrl;
    private String webSocketApiUrl;
}
