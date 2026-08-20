package com.lxf.bian_bot.model;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SymbolRule {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 最小下单数量
     * LOT_SIZE
     */
    private BigDecimal minQty;

    /**
     * 最大下单数量
     * LOT_SIZE
     */
    private BigDecimal maxQty;

    /**
     * 数量步长
     * LOT_SIZE
     */
    private BigDecimal stepSize;

    /**
     * 最小价格
     * PRICE_FILTER
     */
    private BigDecimal minPrice;

    /**
     * 最大价格
     * PRICE_FILTER
     */
    private BigDecimal maxPrice;

    /**
     * 价格步长
     * PRICE_FILTER
     */
    private BigDecimal tickSize;

    /**
     * 最小名义价值
     * MIN_NOTIONAL
     */
    private BigDecimal minNotional;

    /**
     * 市价订单数量规则
     * MARKET_LOT_SIZE
     */
    private BigDecimal marketMinQty;

    private BigDecimal marketMaxQty;

    private BigDecimal marketStepSize;

}
