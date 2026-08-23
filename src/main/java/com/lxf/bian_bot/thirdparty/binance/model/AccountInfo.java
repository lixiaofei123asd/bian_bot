package com.lxf.bian_bot.thirdparty.binance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountInfo {

    /**
     * 总钱包余额
     */
    private double totalWalletBalance;

    /**
     * 未实现盈亏
     */
    private double totalUnrealizedProfit;

    /**
     * 保证金余额
     */
    private double totalMarginBalance;

    /**
     * 可用余额
     */
    private double availableBalance;

    /**
     * 可提现余额
     */
    private double maxWithdrawAmount;

    /**
     * 账户是否可以交易
     */
    private boolean canTrade;
}
