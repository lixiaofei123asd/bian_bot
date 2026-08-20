package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.indicator.Indicator;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;

public interface Strategy {

    // 获取策略使用的指标
    List<Indicator> getIndicators();

    // 获取满足条件的数量
    int getRequiredMatchCount();

    // 执行策略
    TradingSignal execute(List<BinanceKline> klines);
}
