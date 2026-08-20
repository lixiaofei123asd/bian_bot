package com.lxf.bian_bot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeverageInfo {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 当前杠杆
     */
    private int leverage;

    /**
     * 最大名义价值
     */
    private double maxNotionalValue;
}
