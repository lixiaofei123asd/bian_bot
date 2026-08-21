package com.lxf.bian_bot;

import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.service.BinanceWebSocketStreamsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class BinanceMultiWebSocketTest {

    @Autowired
    private BinanceWebSocketStreamsService webSocketStreamsService;


    @Test
    public void testMultiSymbol() throws Exception {

        /**
         * 1. 建立 WebSocket
         */
        webSocketStreamsService.connect();


        /**
         * 等待连接建立
         */
        Thread.sleep(2000);


        /**
         * 2. 注册行情监听器
         */
        webSocketStreamsService
                .addBookTickerListener(
                        this::printTicker
                );


        /**
         * 3. 批量订阅
         */
        webSocketStreamsService
                .subscribeBookTicker(
                        List.of(
                                "BTCUSDT",
                                "ETHUSDT",
                                "SOLUSDT",
                                "DOGEUSDT",
                                "BNBUSDT"
                        )
                );


        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "已经启动多币种 WebSocket"
        );

        System.out.println(
                "等待实时行情..."
        );

        System.out.println(
                "========================================"
        );


        /**
         * 保持程序运行
         */
        Thread.sleep(
                Long.MAX_VALUE
        );
    }


    private void printTicker(
            BinanceBookTicker ticker) {

        System.out.println(
                "----------------------------------------"
        );

        System.out.println(
                "Symbol : "
                        + ticker.getSymbol()
        );

        System.out.println(
                "Bid    : "
                        + ticker.getBidPrice()
        );

        System.out.println(
                "BidQty : "
                        + ticker.getBidQuantity()
        );

        System.out.println(
                "Ask    : "
                        + ticker.getAskPrice()
        );

        System.out.println(
                "AskQty : "
                        + ticker.getAskQuantity()
        );

        System.out.println(
                "Update : "
                        + ticker.getUpdateId()
        );

        System.out.println(
                "----------------------------------------"
        );
    }
}
