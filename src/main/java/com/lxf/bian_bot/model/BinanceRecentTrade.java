package com.lxf.bian_bot.model;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Binance 合约最近成交数据。
 */
@Data
public class BinanceRecentTrade {

    /** 成交ID */
    private Long id;

    /** 成交价格 */
    private BigDecimal price;

    /** 成交数量 */
    private BigDecimal quantity;

    /** 成交额 */
    private BigDecimal quoteQuantity;

    /** 成交时间 */
    private Long time;

    /** 是否买方为挂单方 */
    private Boolean buyerMaker;

    /** 是否为RPI成交 */
    private Boolean rpiTrade;
}
