package com.lxf.bian_bot.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class BinanceLongShortRatio {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 多空比
     */
    private BigDecimal longShortRatio;

    /**
     * 多头账户比例
     */
    private BigDecimal longAccount;

    /**
     * 空头账户比例
     */
    private BigDecimal shortAccount;

    /**
     * 时间
     */
    private Long timestamp;
}
