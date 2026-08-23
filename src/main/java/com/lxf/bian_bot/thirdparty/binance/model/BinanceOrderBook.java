package com.lxf.bian_bot.thirdparty.binance.model;

import java.util.List;
import lombok.Data;

/**
 * Binance 合约订单簿。
 */
@Data
public class BinanceOrderBook {

    /** 最后更新ID */
    private Long lastUpdateId;

    /** 消息输出时间 */
    private Long eventTime;

    /** 交易时间 */
    private Long transactionTime;

    /** 买盘 */
    private List<BinanceOrderBookLevel> bids;

    /** 卖盘 */
    private List<BinanceOrderBookLevel> asks;
}
