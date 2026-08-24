package com.lxf.bian_bot.thirdparty.binance.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeverageInfo {

    /** 交易对 */
    private String symbol;

    /** 当前杠杆 */
    private int leverage;

    /** 最大名义价值 */
    private BigDecimal maxNotionalValue;
}
