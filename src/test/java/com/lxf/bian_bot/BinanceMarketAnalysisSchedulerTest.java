package com.lxf.bian_bot;

import com.lxf.bian_bot.service.BinanceMarketAnalysisScheduler;
import com.lxf.bian_bot.service.BinanceWebSocketStreamsService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class BinanceMarketAnalysisSchedulerTest {

    @Autowired
    private BinanceWebSocketStreamsService webSocketStreamsService;

    @Autowired
    private BinanceMarketAnalysisScheduler marketAnalysisScheduler;


    @Test
    void testMarketAnalysisScheduler() throws InterruptedException {

        log.info("========================================");
        log.info("开始测试 Binance Market Analysis");
        log.info("========================================");


        /**
         * 1. 建立 WebSocket 连接
         */
        log.info("正在连接 Binance WebSocket Streams...");

        webSocketStreamsService.connect();


        /**
         * 2. 等待 WebSocket 建立
         *
         * 这里不要立即订阅。
         */
        Thread.sleep(3000);


        /**
         * 3. 订阅 BTCUSDT
         */
        log.info("订阅 BTCUSDT BookTicker...");

        webSocketStreamsService.subscribeBookTicker(
                "BTCUSDT"
        );


        /**
         * 4. 启动市场分析调度器
         */
        log.info("启动 Market Analysis Scheduler...");

        marketAnalysisScheduler.start();


        /**
         * 5. 保持测试程序运行
         *
         * 让 WebSocket 持续接收行情，
         * 同时让 Scheduler 执行分析。
         *
         * 假设：
         *
         * Scheduler = 10秒一次
         *
         * 这里运行 60 秒，
         * 可以看到大约 6 次分析。
         */
        log.info("========================================");
        log.info("WebSocket + Market Analysis 已启动");
        log.info("测试持续 60 秒...");
        log.info("========================================");


        Thread.sleep(60_000);


        /**
         * 6. 停止 Scheduler
         */
        log.info("停止 Market Analysis Scheduler...");

        marketAnalysisScheduler.stop();


        /**
         * 7. 关闭 WebSocket
         */
        log.info("关闭 Binance WebSocket...");

        webSocketStreamsService.close();


        log.info("========================================");
        log.info("Binance Market Analysis 测试结束");
        log.info("========================================");
    }
}
