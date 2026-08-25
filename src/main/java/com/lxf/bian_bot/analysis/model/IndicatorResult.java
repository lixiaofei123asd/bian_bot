package com.lxf.bian_bot.analysis.model;

import com.lxf.bian_bot.analysis.enums.IndicatorAction;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 指标分析结果。
 *
 * <p>Indicator 只负责分析行情并返回结果，不负责下单。</p>
 * <p>不同类型的指标（AI、技术指标、盘口指标等）都统一通过这个对象向策略层提供分析结果。</p>
 */
@Data
@Builder
public class IndicatorResult {

    /** 指标名称。 */
    private String indicatorName;

    /** 分析方向：UP、DOWN、NEUTRAL。 */
    private IndicatorSignal signal;

    /** 建议动作：OPEN、HOLD、CLOSE、WAIT、ADJUST_PROTECTION。 */
    private IndicatorAction action;

    /** 分析置信度，通常为 0~100。 */
    private Integer confidence;

    /** 建议允许的最小入场价格。 */
    private BigDecimal minEntryPrice;

    /** 建议允许的最大入场价格。 */
    private BigDecimal maxEntryPrice;

    /** 指标明确给出的建议入场价格。 */
    private BigDecimal entryPrice;

    /** 建议止盈价格。 */
    private BigDecimal takeProfitPrice;

    /** 建议止损价格。 */
    private BigDecimal stopLossPrice;

    /** 分析结果说明。 */
    private String reason;
}
