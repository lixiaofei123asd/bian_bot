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
public class AccountInfo {

    /** 总钱包余额 */
    private BigDecimal totalWalletBalance;

    /** 未实现盈亏 */
    private BigDecimal totalUnrealizedProfit;

    /** 保证金余额 */
    private BigDecimal totalMarginBalance;

    /** 可用余额 */
    private BigDecimal availableBalance;

    /** 可提现余额 */
    private BigDecimal maxWithdrawAmount;

    /** 账户是否可以交易 */
    private boolean canTrade;
}
