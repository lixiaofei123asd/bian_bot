package com.lxf.bian_bot.thirdparty.binance.service;

import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import java.util.function.Consumer;

public interface BinanceUserDataStreamService {

    void start();

    void stop();

    boolean isConnected();

    void addOrderListener(Consumer<OrderInfo> listener);

    void addPositionListener(Consumer<PositionInfo> listener);
}
