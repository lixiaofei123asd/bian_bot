package com.lxf.bian_bot.analysis;

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

    /**
     * 指标名称。
     */
    private String indicatorName;

    /**
     * 分析方向：UP、DOWN、NEUTRAL。
     */
    private IndicatorSignal signal;

    /**
     * 分析置信度，通常为 0~100。
     * 普通指标如果没有置信度，可以为空。
     */
    private Integer confidence;

    /**
     * 建议允许的最小入场价格。
     */
    private BigDecimal minEntryPrice;

    /**
     * 建议允许的最大入场价格。
     */
    private BigDecimal maxEntryPrice;

    /**
     * 指标明确给出的建议入场价格。
     * 如果指标只提供入场价格区间，可以为空。
     */
    private BigDecimal entryPrice;

    /**
     * 建议止盈价格。
     */
    private BigDecimal takeProfitPrice;

    /**
     * 建议止损价格。
     */
    private BigDecimal stopLossPrice;

    /**
     * 建议使用的 USDT 金额。
     * 这是指标的分析建议，不代表 Indicator 会执行下单。
     */
    private BigDecimal usdtAmount;

    /**
     * 分析结果说明。
     */
    private String reason;
}
