package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;

public interface Indicator {

    // 获取指标名称
    String getName();

    // 计算指标方向
    IndicatorSignal calculate(List<BinanceKline> klines);
}
