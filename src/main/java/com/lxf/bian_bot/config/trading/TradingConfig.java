package com.lxf.bian_bot.config.trading;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易业务配置。
 *
 * <p>该类只提供交易业务需要的配置，不负责读取 application.yaml。</p>
 */
@Getter
@AllArgsConstructor
public class TradingConfig {

    /** 是否允许真正发送交易订单。 */
    private final boolean enabled;

    /** 每次开仓使用的保证金金额（USDT）。实际仓位价值 = 保证金 × 杠杆。 */
    private final BigDecimal marginAmount;

    /** 下单模式：LIMIT 或 MARKET。 */
    private final String orderType;

    /** 限价单价格来源。 */
    private final String limitPriceSource;

    /** 是否启用止盈止损。 */
    private final boolean takeProfitStopLossEnabled;

    /** 止盈金额（USDT）。 */
    private final BigDecimal takeProfitAmount;

    /** 止损金额（USDT）。 */
    private final BigDecimal stopLossAmount;

    /** 保证金模式：ISOLATED 或 CROSSED。 */
    private final String marginType;

    /** 是否使用交易对允许的最大杠杆。 */
    private final boolean useMaxLeverage;

    /** 目标杠杆；useMaxLeverage=true 时忽略。 */
    private final long leverage;

    /** 是否在下单前自动设置杠杆。 */
    private final boolean autoSetLeverage;
}
