package com.lxf.bian_bot.web;

import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Profile("local")
@RequestMapping("/api/cache")
public class CacheMonitorController {

    private final KlineCache klineCache;
    private final BinanceProperties properties;

    public CacheMonitorController(KlineCache klineCache, BinanceProperties properties) {
        this.klineCache = klineCache;
        this.properties = properties;
    }

    @GetMapping("/kline")
    public Map<String, Object> getKlineCache() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> symbols = new LinkedHashMap<>();
        List<String> configuredSymbols = properties.getSymbols();

        for (String symbol : configuredSymbols) {
            List<BinanceKline> klines = klineCache.get(symbol, "1m");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("interval", "1m");
            data.put("size", klines.size());
            data.put("latest", klines.isEmpty() ? null : klines.getLast());
            symbols.put(symbol, data);
        }

        result.put("type", "Caffeine KlineCache");
        result.put("symbols", symbols);
        return result;
    }

    @GetMapping("/kline/{symbol}/{interval}")
    public Map<String, Object> getKlineCache(@PathVariable String symbol, @PathVariable String interval) {
        List<BinanceKline> klines = klineCache.get(symbol, interval);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol.toUpperCase());
        result.put("interval", interval.toLowerCase());
        result.put("size", klines.size());
        result.put("klines", klines);
        return result;
    }
}
