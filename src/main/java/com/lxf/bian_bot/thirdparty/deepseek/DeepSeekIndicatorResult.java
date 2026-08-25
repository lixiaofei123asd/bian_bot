package com.lxf.bian_bot.thirdparty.deepseek;

import com.lxf.bian_bot.analysis.enums.IndicatorAction;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import java.math.BigDecimal;
import lombok.Data;

/**
 * DeepSeek 返回的指标分析结果。
 *
 * <p>这是 DeepSeek API 的返回 DTO，最终会转换成统一的 IndicatorResult。</p>
 */
@Data
public class DeepSeekIndicatorResult {

    /** 分析方向：UP、DOWN、NEUTRAL。 */
    private IndicatorSignal signal;

    /** 建议动作：OPEN、HOLD、CLOSE、WAIT、ADJUST_PROTECTION。 */
    private IndicatorAction action;

    /** 置信度，0~100。 */
    private Integer confidence;

    /** 最小建议入场价格。 */
    private BigDecimal minEntryPrice;

    /** 最大建议入场价格。 */
    private BigDecimal maxEntryPrice;

    /** 建议入场价格，可以为空。 */
    private BigDecimal entryPrice;

    /** 建议止盈价格。 */
    private BigDecimal takeProfitPrice;

    /** 建议止损价格。 */
    private BigDecimal stopLossPrice;

    /** 分析原因。 */
    private String reason;
}
