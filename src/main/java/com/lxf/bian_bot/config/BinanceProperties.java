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

    /** Binance WebSocket Streams 配置。 */
    private Streams streams = new Streams();

    public List<String> getSymbols() {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        return Arrays.stream(symbol.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Data
    public static class Streams {

        /** 是否使用代理。 */
        private boolean proxyEnabled;

        /** 代理主机。 */
        private String proxyHost;

        /** 代理端口。 */
        private int proxyPort;

        /** Binance WebSocket Streams 地址。 */
        private String webSocketStreamsUrl;
    }
}
