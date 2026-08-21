package com.lxf.bian_bot;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Period;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceLongShortRatio;
import com.lxf.bian_bot.service.BinanceMarketService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

@SpringBootTest
public class BinanceMarketTest {

    @Autowired
    private BinanceMarketService marketService;

    @Test
    public void getKlinesTest(){

        String symbol = "BTCUSDT";
        List<BinanceLongShortRatio> klines = marketService.getLongShortRatio(symbol, Period.PERIOD_1d, 10L);
        klines.forEach(System.out::println);

    }
}
