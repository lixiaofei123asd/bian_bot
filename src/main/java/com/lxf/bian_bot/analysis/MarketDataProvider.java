package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.thirdparty.binance.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceSecondKline;
import java.util.List;

/**
 * 指标使用的市场数据访问入口。
 *
 * Indicator 不直接依赖具体 Cache，由这里统一提供数据。
 */
public interface MarketDataProvider {

    /**
     * 获取指定周期最近的 K 线。
     */
    List<BinanceKline> getKlines(String symbol, String interval, int limit);

    /**
     * 获取最近的 1 秒 K 线，并包含当前正在形成的 K 线。
     */
    List<BinanceSecondKline> getSecondKlinesIncludingCurrent(String symbol, int limit);
}
