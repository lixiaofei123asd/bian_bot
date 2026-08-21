package com.lxf.bian_bot.trading.state;

public enum TradingExecutionState {

    // 当前没有交易执行任务
    READY,

    // 正在检查持仓和当前委托
    CHECKING,

    // 正在提交订单
    SUBMITTING,

    // 订单已经提交，等待订单完成
    ORDER_OPEN,

    // 订单成交并且已经形成持仓
    POSITION_OPEN,

    // 交易执行失败
    FAILED
}
