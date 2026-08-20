package com.lxf.bian_bot.model;

import com.lxf.bian_bot.indicator.TradingSignal;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class BinanceMarketAnalysis {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 当前价格
     */
    private BigDecimal currentPrice;

    /**
     * EMA7
     */
    private BigDecimal ema7;

    /**
     * EMA25
     */
    private BigDecimal ema25;

    /**
     * EMA99
     */
    private BigDecimal ema99;

    /**
     * RSI
     */
    private BigDecimal rsi;

    /**
     * MACD
     */
    private BigDecimal macd;

    /**
     * 多空比
     */
    private BigDecimal longShortRatio;

    /**
     * 最终交易信号
     */
    private TradingSignal signal;

}
