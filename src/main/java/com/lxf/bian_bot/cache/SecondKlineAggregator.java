package com.lxf.bian_bot.cache;

import com.lxf.bian_bot.model.BinanceAggTrade;
import com.lxf.bian_bot.model.BinanceSecondKline;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 将 Binance aggTrade 实时成交数据聚合成 1 秒 K 线。
 */
@Component
public class SecondKlineAggregator {

    private final SecondKlineCache cache;

    public SecondKlineAggregator(SecondKlineCache cache) {
        this.cache = cache;
    }

    /**
     * 处理一笔聚合成交。
     */
    public synchronized void accept(BinanceAggTrade trade) {
        if (trade == null || trade.getSymbol() == null || trade.getPrice() == null
                || trade.getQuantity() == null || trade.getTradeTime() == null) {
            return;
        }

        String symbol = trade.getSymbol().trim().toUpperCase();
        long secondOpenTime = (trade.getTradeTime() / 1000L) * 1000L;
        BinanceSecondKline current = cache.getCurrent(symbol);

        if (current == null) {
            cache.updateCurrent(create(symbol, secondOpenTime, trade));
            return;
        }

        if (current.getOpenTime().equals(secondOpenTime)) {
            update(current, trade);
            cache.updateCurrent(current);
            return;
        }

        if (secondOpenTime > current.getOpenTime()) {
            cache.complete(current);
            cache.updateCurrent(create(symbol, secondOpenTime, trade));
            return;
        }

        // 极少数情况下网络消息乱序，旧成交不能修改已经进入下一秒的K线。
    }

    private BinanceSecondKline create(String symbol, long openTime, BinanceAggTrade trade) {
        BinanceSecondKline kline = new BinanceSecondKline();
        kline.setSymbol(symbol);
        kline.setOpenTime(openTime);
        kline.setOpenPrice(trade.getPrice());
        kline.setHighPrice(trade.getPrice());
        kline.setLowPrice(trade.getPrice());
        kline.setClosePrice(trade.getPrice());
        kline.setVolume(trade.getQuantity());
        kline.setTradeCount(1);
        kline.setLastTradeTime(trade.getTradeTime());
        return kline;
    }

    private void update(BinanceSecondKline kline, BinanceAggTrade trade) {
        BigDecimal price = trade.getPrice();
        kline.setHighPrice(kline.getHighPrice().max(price));
        kline.setLowPrice(kline.getLowPrice().min(price));
        kline.setClosePrice(price);
        kline.setVolume(kline.getVolume().add(trade.getQuantity()));
        kline.setTradeCount(kline.getTradeCount() + 1);
        kline.setLastTradeTime(trade.getTradeTime());
    }
}
