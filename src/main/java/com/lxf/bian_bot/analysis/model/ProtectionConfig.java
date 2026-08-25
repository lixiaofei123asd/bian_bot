package com.lxf.bian_bot.analysis.model;

import java.math.BigDecimal;

/**
 * 策略计算出的仓位保护配置。
 *
 * <p>具体策略决定是否启用保护以及 TP/SL 价格；交易执行层只负责把结果发送给 Binance。</p>
 */
public record ProtectionConfig(
        boolean enabled,
        BigDecimal takeProfitPrice,
        BigDecimal stopLossPrice
) {

    public static ProtectionConfig disabled() {
        return new ProtectionConfig(false, null, null);
    }

    public static ProtectionConfig of(BigDecimal takeProfitPrice, BigDecimal stopLossPrice) {
        return new ProtectionConfig(true, takeProfitPrice, stopLossPrice);
    }
}
