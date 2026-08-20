package com.lxf.bian_bot.repository;

import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;

public interface KlineRepository {

    void initialize();

    void save(BinanceKline kline);

    void saveAll(List<BinanceKline> klines);

    List<BinanceKline> findLatest(String symbol, String interval, int limit);
}
