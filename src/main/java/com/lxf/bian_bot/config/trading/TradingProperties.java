package com.lxf.bian_bot.config.trading;

import java.math.BigDecimal;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    /** 是否允许策略信号真正发送订单。 */
    private boolean enabled;

    /** 每次开仓使用的 USDT 金额。 */
    private BigDecimal usdtAmount = new BigDecimal("10.0");

    /** 下单模式：LIMIT=限价单，MARKET=市价单。 */
    private String orderType = "LIMIT";

    /** 限价单价格来源：LATEST_PRICE=当前最新价格。 */
    private String limitPriceSource = "LATEST_PRICE";

    /** 是否启用止盈止损。 */
    private boolean takeProfitStopLossEnabled = true;

    /** 止盈金额（USDT）。 */
    private BigDecimal takeProfitAmount = new BigDecimal("0.02");

    /** 止损金额（USDT）。 */
    private BigDecimal stopLossAmount = new BigDecimal("0.03");

    /** 保证金模式：ISOLATED=逐仓，CROSSED=全仓。 */
    private String marginType = "ISOLATED";

    /** 是否使用交易对允许的最大杠杆；为 true 时忽略 leverage。 */
    private boolean useMaxLeverage = false;

    /** 目标杠杆；useMaxLeverage=true 时失效。 */
    private long leverage = 20L;

    /** 是否在下单前自动设置杠杆。 */
    private boolean autoSetLeverage = true;
}
