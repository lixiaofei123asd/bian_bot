package com.lxf.bian_bot.analysis.indicator;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 测试 IndicatorDeepSeek 实际拼接给 DeepSeek 的行情数据。
 *
 * <p>注意：这个测试不会调用 DeepSeek API，只会调用 Binance 行情接口，
 * 然后把 IndicatorDeepSeek 组装出来的 userPrompt 原样打印出来。</p>
 */
@SpringBootTest
class IndicatorDeepSeekTest {

    @Autowired
    private IndicatorDeepSeek indicatorDeepSeek;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void printDeepSeekPrompt() throws Exception {
        String symbol = "BTCUSDT";

        // 调用 IndicatorDeepSeek 内部现有的数据拼接逻辑。
        // 不直接调用 calculate()，避免真正请求 DeepSeek。
        @SuppressWarnings("unchecked")
        Map<String, Object> marketData = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                indicatorDeepSeek,
                "collectMarketData",
                symbol
        );

        String userPrompt = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(marketData);

        System.out.println();
        System.out.println("============================================================");
        System.out.println("IndicatorDeepSeek -> DeepSeek 实际发送的数据");
        System.out.println("symbol = " + symbol);
        System.out.println("============================================================");
        System.out.println(userPrompt);
        System.out.println("============================================================");
        System.out.println("数据结束");
        System.out.println("============================================================");
    }
}
