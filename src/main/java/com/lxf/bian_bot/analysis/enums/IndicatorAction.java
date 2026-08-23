package com.lxf.bian_bot.analysis.enums;

/**
 * Indicator 对交易策略的建议动作。
 *
 * <p>Indicator 只返回建议，不直接执行交易。</p>
 */
public enum IndicatorAction {

    /** 建议开仓。 */
    OPEN,

    /** 建议继续持有当前仓位。 */
    HOLD,

    /** 建议平仓。 */
    CLOSE,

    /** 建议等待。 */
    WAIT,

    /** 建议调整当前持仓的止盈止损。 */
    ADJUST_PROTECTION
}
