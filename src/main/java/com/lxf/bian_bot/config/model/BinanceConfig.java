package com.lxf.bian_bot.config.model;

/**
 * Binance 业务配置。
 *
 * <p>该类只提供业务代码需要的配置，不负责直接读取 application.yaml。</p>
 */
public class BinanceConfig {

    private final String webSocketStreamsUrl;
    private final boolean proxyEnabled;
    private final String proxyHost;
    private final int proxyPort;

    public BinanceConfig(String webSocketStreamsUrl,
                         boolean proxyEnabled,
                         String proxyHost,
                         int proxyPort) {
        this.webSocketStreamsUrl = webSocketStreamsUrl;
        this.proxyEnabled = proxyEnabled;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public String getWebSocketStreamsUrl() {
        return webSocketStreamsUrl;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }
}
