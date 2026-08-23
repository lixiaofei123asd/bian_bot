package com.lxf.bian_bot.deepseek;

import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import lombok.Data;

/**
 * DeepSeek 对行情的分析结果。
 */
@Data
public class DeepSeekIndicatorResult {

    private IndicatorSignal signal;

    private Integer confidence;

    private String reason;
}
