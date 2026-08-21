package com.lxf.bian_bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    // 是否允许策略信号真正发送订单
    private boolean enabled;

    // 每次开仓使用的USDT金额
    private double usdtAmount = 5.0;

    // 目标杠杆
    private long leverage = 20L;

    // 是否在下单前自动设置杠杆
    private boolean autoSetLeverage = true;
}
