package com.lxf.bian_bot.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易业务配置。
 *
 * <p>该类只提供交易业务需要的配置，不负责读取 application.yml。</p>
 */
@Getter
@AllArgsConstructor
public class TradingConfig {

    /** 是否允许真正发送交易订单。 */
    private final boolean enabled;

    /** 每次开仓使用的 USDT 金额。 */
    private final double usdtAmount;

    /** 目标杠杆。 */
    private final long leverage;

    /** 是否在下单前自动设置杠杆。 */
    private final boolean autoSetLeverage;
}
