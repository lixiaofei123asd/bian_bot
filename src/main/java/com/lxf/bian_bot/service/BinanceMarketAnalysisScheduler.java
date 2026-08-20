package com.lxf.bian_bot.service;

public interface BinanceMarketAnalysisScheduler {

    /**
     * 启动市场分析
     */
    void start();

    /**
     * 停止市场分析
     */
    void stop();

    /**
     * 是否正在运行
     */
    boolean isRunning();
}
