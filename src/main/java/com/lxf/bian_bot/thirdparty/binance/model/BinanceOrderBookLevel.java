package com.lxf.bian_bot.thirdparty.binance.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Binance 合约订单簿单档数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinanceOrderBookLevel {

    /** 价格 */
    private BigDecimal price;

    /** 数量 */
    private BigDecimal quantity;
}
