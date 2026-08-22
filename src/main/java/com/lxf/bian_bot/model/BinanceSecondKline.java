package com.lxf.bian_bot.model;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 本地聚合的 1 秒 K 线。
 * Binance Futures 没有直接提供 1s Kline Stream，因此由 aggTrade 在本地聚合。
 */
@Data
public class BinanceSecondKline {

    /** 交易对 */
    private String symbol;

    /** 这一秒的开始时间（毫秒） */
    private Long openTime;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 最高价 */
    private BigDecimal highPrice;

    /** 最低价 */
    private BigDecimal lowPrice;

    /** 最新成交价 */
    private BigDecimal closePrice;

    /** 这一秒累计成交量 */
    private BigDecimal volume;

    /** 成交笔数（aggTrade 条数） */
    private long tradeCount;

    /** 最后一次成交时间 */
    private Long lastTradeTime;
}
