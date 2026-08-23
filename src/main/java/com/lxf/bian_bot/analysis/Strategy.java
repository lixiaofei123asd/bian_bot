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

    /**
     * 判断当前策略是否需要在此时执行。
     *
     * <p>执行频率由具体 Strategy 自己决定，外部只负责周期性询问，
     * 不应该知道策略内部到底是 10 秒、3 分钟还是 5 分钟执行一次。</p>
     */
    boolean shouldExecute(String symbol);

    /**
     * 执行策略，策略内部的指标自行获取市场数据。
     */
    TradingSignal execute(String symbol);
}
