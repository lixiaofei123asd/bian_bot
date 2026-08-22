package com.lxf.bian_bot.service;

import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import java.util.function.Consumer;

public interface BinanceUserDataStreamService {

    void start();

    void stop();

    boolean isConnected();

    void addOrderListener(Consumer<OrderInfo> listener);

    void addPositionListener(Consumer<PositionInfo> listener);
}
