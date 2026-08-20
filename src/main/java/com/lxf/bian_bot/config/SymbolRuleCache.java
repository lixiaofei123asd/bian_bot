package com.lxf.bian_bot.config;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.ExchangeInformationResponse;
import com.lxf.bian_bot.model.SymbolRule;
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

    public List<String> filterValidSymbols(List<String> symbols) {
        return symbols.stream()
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .filter(symbol -> rules.containsKey(symbol.toUpperCase()))
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }
}
