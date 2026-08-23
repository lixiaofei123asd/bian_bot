package com.lxf.bian_bot.analysis;

/**
 * 需要由系统定时驱动的策略。
 *
 * <p>只有当前正在使用的 Strategy 实现了这个接口时，StrategyScheduler 才会调用它。
 * 普通策略不实现这个接口，因此不会触发定时逻辑。</p>
 */
public interface ScheduledStrategy extends Strategy {

    /**
     * 执行一次策略自己的定时任务。
     *
     * <p>例如 AI 策略可以在这里获取行情、持仓、订单并调用 AI，
     * 然后根据 AI 返回结果处理当前交易计划。</p>
     *
     * @param symbol 交易对
     */
    void scheduledExecute(String symbol);
}
