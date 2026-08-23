package com.lxf.bian_bot.thirdparty.binance.model;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Binance 合约 24 小时价格变动统计。
 */
@Data
public class Binance24hTicker {

    /** 交易对 */
    private String symbol;

    /** 价格变动 */
    private BigDecimal priceChange;

    /** 价格变动百分比 */
    private BigDecimal priceChangePercent;

    /** 加权平均价格 */
    private BigDecimal weightedAvgPrice;

    /** 最新价格 */
    private BigDecimal lastPrice;

    /** 最新成交数量 */
    private BigDecimal lastQty;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 24小时最高价 */
    private BigDecimal highPrice;

    /** 24小时最低价 */
    private BigDecimal lowPrice;

    /** 24小时成交量 */
    private BigDecimal volume;

    /** 24小时成交额 */
    private BigDecimal quoteVolume;

    /** 统计开始时间 */
    private Long openTime;

    /** 统计结束时间 */
    private Long closeTime;

    /** 第一笔成交ID */
    private Long firstId;

    /** 最后一笔成交ID */
    private Long lastId;

    /** 成交笔数 */
    private Long count;
}
