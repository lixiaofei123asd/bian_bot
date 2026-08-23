package com.lxf.bian_bot.analysis.indicator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.MarketDataProvider;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.deepseek.DeepSeekClient;
import com.lxf.bian_bot.deepseek.DeepSeekIndicatorResult;
import com.lxf.bian_bot.model.Binance24hTicker;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.BinanceMarkPrice;
import com.lxf.bian_bot.model.BinanceOrderBook;
import com.lxf.bian_bot.model.BinancePriceTicker;
import com.lxf.bian_bot.model.BinanceRecentTrade;
import com.lxf.bian_bot.model.BinanceSecondKline;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 使用 DeepSeek 分析当前合约行情并返回 IndicatorSignal。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndicatorDeepSeek implements Indicator {

    private static final int SECOND_KLINE_LIMIT = 20;
    private static final long ORDER_BOOK_LIMIT = 20L;
    private static final long RECENT_TRADE_LIMIT = 100L;

    private final MarketDataProvider marketDataProvider;
    private final com.lxf.bian_bot.service.BinanceMarketService marketService;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "DEEPSEEK";
    }

    @Override
    public IndicatorSignal calculate(String symbol) {
        try {
            Map<String, Object> marketData = collectMarketData(symbol);
            String userPrompt = objectMapper.writeValueAsString(marketData);

            String response = deepSeekClient.chat(SYSTEM_PROMPT, userPrompt);
            DeepSeekIndicatorResult result = objectMapper.readValue(response, DeepSeekIndicatorResult.class);

            if (result.getSignal() == null) {
                log.warn("DeepSeek 未返回有效方向: symbol={}, response={}", symbol, response);
                return IndicatorSignal.NEUTRAL;
            }

            log.info("DeepSeek指标: symbol={}, signal={}, confidence={}, reason={}",
                    symbol, result.getSignal(), result.getConfidence(), result.getReason());
            return result.getSignal();
        } catch (Exception e) {
            log.error("DeepSeek指标分析失败: symbol={}", symbol, e);
            return IndicatorSignal.NEUTRAL;
        }
    }

    private Map<String, Object> collectMarketData(String symbol) {
        BinancePriceTicker price = marketService.getPrice(symbol);
        BinanceMarkPrice markPrice = marketService.getMarkPrice(symbol);
        Binance24hTicker ticker24h = marketService.get24hTicker(symbol);
        BinanceBookTicker bookTicker = marketService.getBookTicker(symbol);
        BinanceOrderBook orderBook = marketService.getOrderBook(symbol, ORDER_BOOK_LIMIT);
        List<BinanceRecentTrade> recentTrades = marketService.getRecentTrades(symbol, RECENT_TRADE_LIMIT);
        List<BinanceSecondKline> secondKlines =
                marketDataProvider.getSecondKlinesIncludingCurrent(symbol, SECOND_KLINE_LIMIT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("symbol", symbol);
        data.put("price", price);
        data.put("markPrice", markPrice);
        data.put("ticker24h", ticker24h);
        data.put("bookTicker", bookTicker);
        data.put("orderBook", orderBook);
        data.put("recentTrades", recentTrades);
        data.put("secondKlines", secondKlines);
        return data;
    }

    private static final String SYSTEM_PROMPT = """
            你是一个短线合约行情分析器。
            你的任务是根据提供的 Binance 合约实时行情数据，判断当前极短线方向。

            只能返回 JSON，不要返回 Markdown，不要返回解释性文字。
            JSON 格式必须严格为：
            {"signal":"UP|DOWN|NEUTRAL","confidence":0,"reason":"简短原因"}

            signal 含义：
            UP = 当前更偏向上涨
            DOWN = 当前更偏向下跌
            NEUTRAL = 多空没有足够明确的优势

            请综合考虑：
            1. 最近 1 秒 K 线的连续方向和反转情况；
            2. 最新成交价格和短线价格变化；
            3. Order Book 买卖盘力量；
            4. 最近成交的买卖方向和成交量；
            5. Mark Price、Index Price、Funding Rate；
            6. 24 小时价格变化。

            不要因为单独一个字段就判断方向。对于信号不明确的情况返回 NEUTRAL。
            confidence 返回 0 到 100 的整数。
            """;
}
