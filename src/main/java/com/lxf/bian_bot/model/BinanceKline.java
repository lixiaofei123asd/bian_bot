package com.lxf.bian_bot.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BinanceKline {

    /**
     * 开盘时间
     */
    private Long openTime;

    /**
     * 开盘价
     */
    private BigDecimal openPrice;

    /**
     * 最高价
     */
    private BigDecimal highPrice;

    /**
     * 最低价
     */
    private BigDecimal lowPrice;

    /**
     * 收盘价
     */
    private BigDecimal closePrice;

    /**
     * 成交量
     */
    private BigDecimal volume;

    /**
     * 收盘时间
     */
    private Long closeTime;

    /**
     * 成交额
     */
    private BigDecimal quoteAssetVolume;

    /**
     * 成交笔数
     */
    private Long numberOfTrades;

    /**
     * 主动买入成交量
     */
    private BigDecimal takerBuyBaseAssetVolume;

    /**
     * 主动买入成交额
     */
    private BigDecimal takerBuyQuoteAssetVolume;
}
