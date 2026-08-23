package com.lxf.bian_bot.thirdparty.binance.enums;

public enum OrderType {


    /**
     * 限价单
     */
    LIMIT,

    /**
     * 市价单
     */
    MARKET,

    /**
     * 止损限价单
     */
    STOP,

    /**
     * 止损市价单
     */
    STOP_MARKET,

    /**
     * 止盈限价单
     */
    TAKE_PROFIT,

    /**
     * 止盈市价单
     */
    TAKE_PROFIT_MARKET,

    /**
     * 移动止损
     */
    TRAILING_STOP_MARKET

}
