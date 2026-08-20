package com.lxf.bian_bot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderInfo {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * Binance Order ID
     */
    private long orderId;

    /**
     * 客户端订单 ID
     */
    private String clientOrderId;

    /**
     * BUY / SELL
     */
    private String side;

    /**
     * MARKET / LIMIT 等
     */
    private String type;

    /**
     * 原始数量
     */
    private double origQty;

    /**
     * 已成交数量
     */
    private double executedQty;

    /**
     * 成交均价
     */
    private double avgPrice;

    /**
     * 订单价格
     */
    private double price;

    /**
     * 订单状态
     */
    private String status;

    /**
     * 是否 ReduceOnly
     */
    private boolean reduceOnly;

    /**
     * 持仓方向
     */
    private String positionSide;

    /**
     * 创建/更新时间
     */
    private long updateTime;
}
