package com.lxf.bian_bot.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TradingDecision {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 决策
     */
    private DecisionType decision;

    /**
     * 入场方向
     */
    private Direction direction;

    /**
     * 当前价格
     */
    private BigDecimal currentPrice;

    /**
     * 计划入场价格
     */
    private BigDecimal entryPrice;

    /**
     * 止损价格
     */
    private BigDecimal stopLossPrice;

    /**
     * 止盈价格
     */
    private BigDecimal takeProfitPrice;

    /**
     * 风险
     */
    private BigDecimal risk;

    /**
     * 收益
     */
    private BigDecimal reward;

    /**
     * 盈亏比
     */
    private BigDecimal riskRewardRatio;

    /**
     * 决策原因
     */
    private String reason;


    public enum DecisionType {

        /**
         * 等待
         */
        WAIT,

        /**
         * 开多
         */
        ENTER_LONG,

        /**
         * 开空
         */
        ENTER_SHORT,

        /**
         * 平仓
         */
        EXIT

    }


    public enum Direction {

        LONG,

        SHORT

    }
}
