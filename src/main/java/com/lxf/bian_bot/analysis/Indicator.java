package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.analysis.model.IndicatorResult;

public interface Indicator {

    // 获取指标名称
    String getName();

    // 指标自行根据交易对获取所需市场数据并返回分析结果
    IndicatorResult calculate(String symbol);
}
