package com.lxf.bian_bot.service;

import com.lxf.bian_bot.model.AccountInfo;

public interface BinanceAccountService {

    /**
     * 查询账户信息
     */
    AccountInfo getAccountInfo();
}
