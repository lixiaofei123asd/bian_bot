package com.lxf.bian_bot.model;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Binance 合约标记价格及资金费率信息。
 */
@Data
public class BinanceMarkPrice {

    /** 交易对 */
    private String symbol;

    /** 标记价格 */
    private BigDecimal markPrice;

    /** 指数价格 */
    private BigDecimal indexPrice;

    /** 预计结算价 */
    private BigDecimal estimatedSettlePrice;

    /** 最近一次资金费率 */
    private BigDecimal lastFundingRate;

    /** 利率 */
    private BigDecimal interestRate;

    /** 下一次资金费时间 */
    private Long nextFundingTime;

    /** 数据时间 */
    private Long time;
}
