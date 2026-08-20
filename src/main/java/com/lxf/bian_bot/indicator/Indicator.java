package com.lxf.bian_bot.indicator;

import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import java.util.List;

public interface Indicator {

    /**
     * 指标名称
     */
    Strategy name();

    /**
     * 是否启用
     */
    boolean enabled();

    /**
     * 计算指标
     */
    void calculate(List<BinanceKline> klines, BinanceMarketAnalysis analysis);
}
