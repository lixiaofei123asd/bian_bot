package com.lxf.bian_bot.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TradingDecision {

    /** 交易对 */
    private String symbol;

    /** 决策 */
    private DecisionType decision;

    /** 入场方向 */
    private Direction direction;

    /** 当前价格 */
    private BigDecimal currentPrice;

    /** 建议最小入场价格 */
    private BigDecimal minEntryPrice;

    /** 建议最大入场价格 */
    private BigDecimal maxEntryPrice;

    /** 计划入场价格 */
    private BigDecimal entryPrice;

    /** 本次开仓使用的保证金金额，不是仓位名义价值。 */
    private BigDecimal marginAmount;

    /** 策略计算出的最终止损价格。 */
    private BigDecimal stopLossPrice;

    /** 策略计算出的最终止盈价格。 */
    private BigDecimal takeProfitPrice;

    /** 风险 */
    private BigDecimal risk;

    /** 收益 */
    private BigDecimal reward;

    /** 盈亏比 */
    private BigDecimal riskRewardRatio;

    /** 决策原因 */
    private String reason;

    public enum DecisionType {
        WAIT,
        ENTER_LONG,
        ENTER_SHORT,
        HOLD,
        EXIT,
        ADJUST_PROTECTION
    }

    public enum Direction {
        LONG,
        SHORT
    }
}
