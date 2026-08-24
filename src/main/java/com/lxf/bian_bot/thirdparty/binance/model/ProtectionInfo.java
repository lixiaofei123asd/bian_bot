package com.lxf.bian_bot.thirdparty.binance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前交易对的仓位级止盈止损保护价格。
 *
 * <p>数据来源：程序启动时由 Binance REST 初始化，运行期间由 User Data Stream 的 ALGO_UPDATE 更新。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtectionInfo {

    /** 交易对。 */
    private String symbol;

    /** 当前止盈触发价格。没有止盈时为 0。 */
    private double takeProfitPrice;

    /** 当前止损触发价格。没有止损时为 0。 */
    private double stopLossPrice;

    /** Binance 止盈 Algo ID。 */
    private long takeProfitAlgoId;

    /** Binance 止损 Algo ID。 */
    private long stopLossAlgoId;
}
