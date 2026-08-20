package com.lxf.bian_bot.model;

import lombok.Data;

@Data
public class BinanceStreamMessage<T> {

    /**
     * Stream 名称
     *
     * 例如：
     * btcusdt@bookTicker
     */
    private String stream;

    /**
     * 实际数据
     */
    private T data;
}
