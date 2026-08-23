package com.lxf.bian_bot.thirdparty.binance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionInfo {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 持仓数量
     *
     * > 0 = LONG
     * < 0 = SHORT
     * = 0 = 无仓位
     */
    private double positionAmt;

    /**
     * 开仓均价
     */
    private double entryPrice;

    /**
     * 盈亏平衡价格
     */
    private double breakEvenPrice;

    /**
     * 当前标记价格
     */
    private double markPrice;

    /**
     * 名义价值
     */
    private double notional;

    /**
     * 未实现盈亏
     */
    private double unrealizedProfit;

    /**
     * 杠杆
     */
    private int leverage;

    /**
     * 保证金模式
     */
    private String marginType;

    /**
     * 持仓方向
     *
     * BOTH / LONG / SHORT
     */
    private String positionSide;

    /**
     * 强平价格
     */
    private double liquidationPrice;

    /**
     * 是否自动追加保证金
     */
    private boolean autoAddMargin;

    /**
     * 判断是否有仓位
     */
    public boolean hasPosition() {
        return Math.abs(positionAmt) > 0.00000001;
    }

    /**
     * 判断是否多仓
     */
    public boolean isLong() {
        return positionAmt > 0;
    }

    /**
     * 判断是否空仓
     */
    public boolean isShort() {
        return positionAmt < 0;
    }
}
