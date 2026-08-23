package com.lxf.bian_bot.analysis.indicator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.IndicatorResult;
import com.lxf.bian_bot.analysis.MarketDataProvider;
import com.lxf.bian_bot.analysis.enums.IndicatorAction;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.deepseek.DeepSeekClient;
import com.lxf.bian_bot.deepseek.DeepSeekIndicatorResult;
import com.lxf.bian_bot.model.Binance24hTicker;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.BinanceMarkPrice;
import com.lxf.bian_bot.model.BinanceOrderBook;
import com.lxf.bian_bot.model.BinancePriceTicker;
import com.lxf.bian_bot.model.BinanceRecentTrade;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 使用 DeepSeek 分析当前合约行情并返回 IndicatorResult。
 *
 * <p>Indicator 只负责分析，不负责下单。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndicatorDeepSeek implements Indicator {

    private static final int KLINE_15M_LIMIT = 50;
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
    public IndicatorResult calculate(String symbol) {
        return calculateWithContext(symbol, Map.of());
    }

    /**
     * 在普通行情数据之外，为 AI 追加策略上下文，例如当前持仓和活动订单。
     */
    public IndicatorResult calculateWithContext(String symbol, Map<String, Object> strategyContext) {
        try {
            Map<String, Object> marketData = collectMarketData(symbol, strategyContext);
            String userPrompt = objectMapper.writeValueAsString(marketData);

            String response = deepSeekClient.chat(SYSTEM_PROMPT, userPrompt);
            DeepSeekIndicatorResult result = objectMapper.readValue(response, DeepSeekIndicatorResult.class);

            if (result.getSignal() == null) {
                log.warn("DeepSeek 未返回有效方向: symbol={}, response={}", symbol, response);
                return neutralResult();
            }

            IndicatorResult indicatorResult = IndicatorResult.builder()
                    .indicatorName(getName())
                    .signal(result.getSignal())
                    .action(result.getAction())
                    .confidence(result.getConfidence())
                    .minEntryPrice(result.getMinEntryPrice())
                    .maxEntryPrice(result.getMaxEntryPrice())
                    .entryPrice(result.getEntryPrice())
                    .takeProfitPrice(result.getTakeProfitPrice())
                    .stopLossPrice(result.getStopLossPrice())
                    .usdtAmount(result.getUsdtAmount())
                    .reason(result.getReason())
                    .build();

            log.info("DeepSeek指标: symbol={}, signal={}, action={}, confidence={}, minEntryPrice={}, maxEntryPrice={}, entryPrice={}, takeProfitPrice={}, stopLossPrice={}, usdtAmount={}, reason={}",
                    symbol,
                    indicatorResult.getSignal(),
                    indicatorResult.getAction(),
                    indicatorResult.getConfidence(),
                    indicatorResult.getMinEntryPrice(),
                    indicatorResult.getMaxEntryPrice(),
                    indicatorResult.getEntryPrice(),
                    indicatorResult.getTakeProfitPrice(),
                    indicatorResult.getStopLossPrice(),
                    indicatorResult.getUsdtAmount(),
                    indicatorResult.getReason());

            return indicatorResult;
        } catch (Exception e) {
            log.error("DeepSeek指标分析失败: symbol={}", symbol, e);
            return neutralResult();
        }
    }

    private IndicatorResult neutralResult() {
        return IndicatorResult.builder()
                .indicatorName(getName())
                .signal(IndicatorSignal.NEUTRAL)
                .action(IndicatorAction.WAIT)
                .confidence(0)
                .build();
    }

    private Map<String, Object> collectMarketData(String symbol, Map<String, Object> strategyContext) {
        BinancePriceTicker price = marketService.getPrice(symbol);
        BinanceMarkPrice markPrice = marketService.getMarkPrice(symbol);
        Binance24hTicker ticker24h = marketService.get24hTicker(symbol);
        BinanceBookTicker bookTicker = marketService.getBookTicker(symbol);
        BinanceOrderBook orderBook = marketService.getOrderBook(symbol, ORDER_BOOK_LIMIT);
        List<BinanceRecentTrade> recentTrades = marketService.getRecentTrades(symbol, RECENT_TRADE_LIMIT);
        List<?> klines15m = marketDataProvider.getKlines(symbol, "15m", KLINE_15M_LIMIT);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("symbol", symbol);
        data.put("currentPrice", price);
        data.put("markPrice", markPrice);
        data.put("ticker24h", ticker24h);
        data.put("bookTicker", bookTicker);
        data.put("orderBook", orderBook);
        data.put("recentTrades", recentTrades);
        data.put("klines15m", klines15m);
        data.putAll(strategyContext);
        return data;
    }

    private static final String SYSTEM_PROMPT = """
            你是一个 Binance USDT-M 合约交易策略分析器。

            你收到的是 15 分钟级别 K 线、实时行情、盘口、最近成交，以及当前账户持仓和活动订单。
            你的任务不是频繁预测下一秒涨跌，而是制定或检查当前交易计划。

            只能返回 JSON，不要返回 Markdown，不要返回解释性文字。
            JSON 格式必须严格为：
            {
              "signal":"UP|DOWN|NEUTRAL",
              "action":"OPEN|HOLD|CLOSE|WAIT|ADJUST_PROTECTION",
              "confidence":0,
              "minEntryPrice":0,
              "maxEntryPrice":0,
              "entryPrice":0,
              "takeProfitPrice":0,
              "stopLossPrice":0,
              "usdtAmount":0,
              "reason":"简短原因"
            }

            action 含义：
            OPEN = 当前没有持仓，建议建立新仓位；
            HOLD = 当前已有仓位，继续持有，不修改现有止盈止损；
            CLOSE = 当前已有仓位，建议平仓；
            WAIT = 当前没有合适的交易机会；
            ADJUST_PROTECTION = 当前已有仓位，建议调整止盈止损。

            重要规则：
            1. 如果当前有持仓，不要返回 OPEN；
            2. 如果没有明显理由改变保护价格，返回 HOLD，不要频繁调整止盈止损；
            3. ADJUST_PROTECTION 只能在趋势、波动或风险结构发生明显变化时使用；
            4. 不要为了每次分析都有变化而修改 TP/SL；
            5. 如果 action=HOLD，可以把 takeProfitPrice、stopLossPrice 返回 null；
            6. 如果 action=WAIT 或 signal=NEUTRAL，交易相关价格和金额可以返回 null；
            7. confidence 返回 0 到 100 的整数。

            对 OPEN：给出合理的入场区间、最佳入场价格、止盈、止损和 USDT 金额。
            对 ADJUST_PROTECTION：只填写确实需要调整的止盈/止损价格。

            请重点分析最近 50 根 15 分钟 K 线形成的趋势、波动、支撑阻力、成交量变化，
            再结合实时价格、Order Book、最近成交、Mark Price、Funding Rate、24小时变化，
            最后结合当前持仓和活动订单给出交易计划。
            """;
}
