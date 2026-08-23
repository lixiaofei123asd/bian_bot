package com.lxf.bian_bot.thirdparty.binance.enums;

public enum TimeInForce {


    /**
     * 一直挂
     */
    GTC,

    /**
     * 能成交多少成交多少，剩下取消
     */
    IOC,

    /**
     * 必须全部成交，否则取消
     */
    FOK,

    /**
     * 挂到指定时间，时间到了自动失效
     */
    GTD
}
