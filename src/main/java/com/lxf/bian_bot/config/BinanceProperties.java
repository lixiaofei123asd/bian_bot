package com.lxf.bian_bot.config;

import java.util.Arrays;
import java.util.List;
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
    private String symbol;

    public List<String> getSymbols() {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        return Arrays.stream(symbol.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
