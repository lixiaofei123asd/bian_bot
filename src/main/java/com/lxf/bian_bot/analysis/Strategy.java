package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.analysis.enums.TradingSignal;
import java.util.List;

public interface Strategy {

    // 获取策略名称，用于配置文件匹配
    String getName();

    // 获取策略使用的指标
    List<Indicator> getIndicators();

    // 获取满足条件的数量
    int getRequiredMatchCount();

    // 执行策略，策略内部的指标自行获取市场数据
    TradingSignal execute(String symbol);
}
