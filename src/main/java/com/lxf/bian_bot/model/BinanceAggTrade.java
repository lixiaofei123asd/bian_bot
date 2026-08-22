package com.lxf.bian_bot.model;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Binance Futures aggTrade（聚合成交）数据。
 *
 * 用于在本地聚合 Binance 官方没有直接提供的 1 秒 K 线。
 */
@Data
public class BinanceAggTrade {

    /** 交易对 */
    private String symbol;

    /** 聚合成交 ID */
    private Long aggregateTradeId;

    /** 成交价格 */
    private BigDecimal price;

    /** 成交数量 */
    private BigDecimal quantity;

    /** 成交时间 */
    private Long tradeTime;
}
