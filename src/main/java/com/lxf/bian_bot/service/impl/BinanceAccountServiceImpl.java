package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.AccountInformationV2Response;
import com.lxf.bian_bot.model.AccountInfo;
import com.lxf.bian_bot.service.BinanceAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class BinanceAccountServiceImpl implements BinanceAccountService {

    private DerivativesTradingUsdsFuturesRestApi api;

    @Autowired
    public BinanceAccountServiceImpl(DerivativesTradingUsdsFuturesRestApi api) {
        this.api = api;
    }

    @Override
    public AccountInfo getAccountInfo() {
        ApiResponse<AccountInformationV2Response> response = api.accountInformationV2(null);
        AccountInformationV2Response data = response.getData();
        return AccountInfo.builder()
                .totalWalletBalance(Double.parseDouble(data.getTotalWalletBalance()))
                .totalUnrealizedProfit(Double.parseDouble(data.getTotalUnrealizedProfit()))
                .totalMarginBalance(Double.parseDouble(data.getTotalMarginBalance()))
                .availableBalance(Double.parseDouble(data.getAvailableBalance()))
                .maxWithdrawAmount(Double.parseDouble(data.getMaxWithdrawAmount()))
                .canTrade(data.getCanTrade())
                .build();
    }
}
