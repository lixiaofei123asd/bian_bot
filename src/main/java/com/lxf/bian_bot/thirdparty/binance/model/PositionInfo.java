package com.lxf.bian_bot.thirdparty.binance.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionInfo {

    /** 交易对 */
    private String symbol;

    /** 持仓数量：> 0 = LONG，< 0 = SHORT，= 0 = 无仓位 */
    private BigDecimal positionAmt;

    /** 开仓均价 */
    private BigDecimal entryPrice;

    /** 盈亏平衡价格 */
    private BigDecimal breakEvenPrice;

    /** 当前标记价格 */
    private BigDecimal markPrice;

    /** 名义价值 */
    private BigDecimal notional;

    /** 未实现盈亏 */
    private BigDecimal unrealizedProfit;

    /** 杠杆 */
    private int leverage;

    /** 保证金模式 */
    private String marginType;

    /** 持仓方向：BOTH / LONG / SHORT */
    private String positionSide;

    /** 强平价格 */
    private BigDecimal liquidationPrice;

    /** 是否自动追加保证金 */
    private boolean autoAddMargin;

    public boolean hasPosition() {
        return positionAmt != null && positionAmt.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean isLong() {
        return positionAmt != null && positionAmt.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isShort() {
        return positionAmt != null && positionAmt.compareTo(BigDecimal.ZERO) < 0;
    }
}
