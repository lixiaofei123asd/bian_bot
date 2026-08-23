package com.lxf.bian_bot.config;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ExchangeInformationResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NotionalAndLeverageBracketsResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NotionalAndLeverageBracketsResponse1;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NotionalAndLeverageBracketsResponse1Inner;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.NotionalAndLeverageBracketsResponse1InnerBracketsInner;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SymbolRuleCache {

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final Map<String, SymbolRule> rules = new ConcurrentHashMap<>();

    public SymbolRuleCache(DerivativesTradingUsdsFuturesRestApi api) {
        this.api = api;
    }

    @PostConstruct
    public void init() {
        log.info("开始初始化 Binance 交易规则...");
        ApiResponse<ExchangeInformationResponse> response = api.exchangeInformation();
        ExchangeInformationResponse data = response.getData();
        data.getSymbols().forEach(e -> {
            String symbol = e.getSymbol();

            AtomicReference<BigDecimal> minPrice = new AtomicReference<>();
            AtomicReference<BigDecimal> maxPrice = new AtomicReference<>();
            AtomicReference<BigDecimal> tickSize = new AtomicReference<>();
            AtomicReference<BigDecimal> minQty = new AtomicReference<>();
            AtomicReference<BigDecimal> maxQty = new AtomicReference<>();
            AtomicReference<BigDecimal> stepSize = new AtomicReference<>();
            AtomicReference<BigDecimal> marketMinQty = new AtomicReference<>();
            AtomicReference<BigDecimal> marketMaxQty = new AtomicReference<>();
            AtomicReference<BigDecimal> marketStepSize = new AtomicReference<>();
            AtomicReference<BigDecimal> minNotional = new AtomicReference<>();

            e.getFilters().forEach(f -> {
                if ("PRICE_FILTER".equalsIgnoreCase(f.getFilterType())) {
                    minPrice.set(new BigDecimal(f.getMinPrice()));
                    maxPrice.set(new BigDecimal(f.getMaxPrice()));
                    tickSize.set(new BigDecimal(f.getTickSize()));
                }
                if ("LOT_SIZE".equalsIgnoreCase(f.getFilterType())) {
                    minQty.set(new BigDecimal(f.getMinQty()));
                    maxQty.set(new BigDecimal(f.getMaxQty()));
                    stepSize.set(new BigDecimal(f.getStepSize()));
                }
                if ("MARKET_LOT_SIZE".equalsIgnoreCase(f.getFilterType())) {
                    marketMinQty.set(new BigDecimal(f.getMinQty()));
                    marketMaxQty.set(new BigDecimal(f.getMaxQty()));
                    marketStepSize.set(new BigDecimal(f.getStepSize()));
                }
                if ("MIN_NOTIONAL".equalsIgnoreCase(f.getFilterType())) {
                    minNotional.set(new BigDecimal(f.getNotional()));
                }
            });

            SymbolRule symbolRule = SymbolRule.builder()
                    .symbol(symbol)
                    .minQty(minQty.get())
                    .maxQty(maxQty.get())
                    .stepSize(stepSize.get())
                    .minPrice(minPrice.get())
                    .maxPrice(maxPrice.get())
                    .tickSize(tickSize.get())
                    .minNotional(minNotional.get())
                    .marketMinQty(marketMinQty.get())
                    .marketMaxQty(marketMaxQty.get())
                    .marketStepSize(marketStepSize.get())
                    .build();
            rules.put(symbol, symbolRule);
        });

        log.info("Binance 交易规则初始化完成，数量：{}", rules.size());
    }

    public SymbolRule get(String symbol) {
        return rules.get(symbol);
    }

    /**
     * 获取交易对允许的最大杠杆。
     *
     * <p>最大杠杆来自 Binance 的 leverageBracket，而不是 exchangeInfo 的 symbol filter。
     * 因此这里采用按需加载，避免启动时为全部交易对逐个请求接口。</p>
     */
    public Long getMaxLeverage(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol 不能为空");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        SymbolRule rule = rules.get(normalizedSymbol);
        if (rule == null) {
            throw new IllegalArgumentException("交易对不存在: " + normalizedSymbol);
        }

        if (rule.getMaxLeverage() != null) {
            return rule.getMaxLeverage();
        }

        synchronized (rule) {
            if (rule.getMaxLeverage() != null) {
                return rule.getMaxLeverage();
            }

            ApiResponse<NotionalAndLeverageBracketsResponse> response =
                    api.notionalAndLeverageBrackets(normalizedSymbol, null);
            NotionalAndLeverageBracketsResponse data = response.getData();

            if (data == null || !(data.getActualInstance() instanceof NotionalAndLeverageBracketsResponse1 bracketResponse)) {
                throw new IllegalStateException("无法获取交易对最大杠杆: " + normalizedSymbol);
            }

            NotionalAndLeverageBracketsResponse1Inner symbolBracket = bracketResponse.stream()
                    .filter(item -> normalizedSymbol.equalsIgnoreCase(item.getSymbol()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("杠杆规则中不存在交易对: " + normalizedSymbol));

            Long maxLeverage = symbolBracket.getBrackets() == null
                    ? null
                    : symbolBracket.getBrackets().stream()
                    .map(NotionalAndLeverageBracketsResponse1InnerBracketsInner::getInitialLeverage)
                    .filter(java.util.Objects::nonNull)
                    .max(Long::compareTo)
                    .orElse(null);

            if (maxLeverage == null || maxLeverage <= 0) {
                throw new IllegalStateException("交易对最大杠杆无效: " + normalizedSymbol);
            }

            rule.setMaxLeverage(maxLeverage);
            log.info("获取交易对最大杠杆: symbol={}, maxLeverage={}", normalizedSymbol, maxLeverage);
            return maxLeverage;
        }
    }

    public List<String> filterValidSymbols(List<String> symbols) {
        return symbols.stream()
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .map(String::toUpperCase)
                .filter(rules::containsKey)
                .distinct()
                .toList();
    }
}
