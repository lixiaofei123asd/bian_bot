package com.lxf.bian_bot.model;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Binance 合约最新价格。
 */
@Data
public class BinancePriceTicker {

    /** 交易对 */
    private String symbol;

    /** 最新价格 */
    private BigDecimal price;

    /** 数据时间 */
    private Long time;
}
