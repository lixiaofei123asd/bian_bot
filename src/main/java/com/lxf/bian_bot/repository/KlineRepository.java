package com.lxf.bian_bot.repository;

import com.lxf.bian_bot.thirdparty.binance.model.BinanceKline;
import java.util.List;

public interface KlineRepository {

    void initialize();

    void save(String interval, BinanceKline kline);

    void saveAll(String interval, List<BinanceKline> klines);

    List<BinanceKline> findLatest(String symbol, String interval, int limit);
}
