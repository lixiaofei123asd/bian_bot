package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.model.BinanceKline;
import java.math.BigDecimal;
import java.util.List;

public interface Indicator {

    // 获取指标名称
    String getName();

    // 计算指标
    BigDecimal calculate(List<BinanceKline> klines);
}
