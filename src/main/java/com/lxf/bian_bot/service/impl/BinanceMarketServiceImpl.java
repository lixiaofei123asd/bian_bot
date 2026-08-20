package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.KlineCandlestickDataResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.LongShortRatioResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Period;
import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceLongShortRatio;
import com.lxf.bian_bot.service.BinanceMarketService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceMarketServiceImpl implements BinanceMarketService {

    private final DerivativesTradingUsdsFuturesRestApi api;
    private final KlineCache klineCache;

    public BinanceMarketServiceImpl(DerivativesTradingUsdsFuturesRestApi api, KlineCache klineCache) {
        this.api = api;
        this.klineCache = klineCache;
    }

    @Override
    public List<BinanceKline> getKlines(String symbol, Interval interval, Long limit) {
        String cacheInterval = interval.toString();
        List<BinanceKline> cachedKlines = klineCache.get(symbol, cacheInterval);
        if (!cachedKlines.isEmpty() && cachedKlines.size() >= limit) {
            log.debug("使用本地 K线缓存: symbol={}, interval={}, limit={}", symbol, interval, limit);
            return getLatestKlines(cachedKlines, limit);
        }

        log.info("获取 Binance K线: symbol={}, interval={}, limit={}", symbol, interval, limit);
        ApiResponse<KlineCandlestickDataResponse> response = api.klineCandlestickData(symbol, interval, null, null, limit);
        List<BinanceKline> klines = parseKlines(response.getData(), symbol);
        klineCache.put(symbol, cacheInterval, klines);
        return klines;
    }

    @Override
    public List<BinanceKline> getKlines(String symbol, Interval interval, Long startTime, Long endTime, Long limit) {
        log.info("获取 Binance K线: symbol={}, interval={}, startTime={}, endTime={}, limit={}", symbol, interval, startTime, endTime, limit);
        ApiResponse<KlineCandlestickDataResponse> response = api.klineCandlestickData(symbol, interval, startTime, endTime, limit);
        List<BinanceKline> klines = parseKlines(response.getData(), symbol);
        klineCache.put(symbol, interval.toString(), klines);
        return klines;
    }

    @Override
    public BinanceKline getLatestKline(String symbol, Interval interval) {
        List<BinanceKline> klines = getKlines(symbol, interval, 1L);
        if (klines == null || klines.isEmpty()) {
            return null;
        }
        return klines.getLast();
    }

    @Override
    public List<BinanceLongShortRatio> getLongShortRatio(String symbol, Period period, Long limit) {
        ApiResponse<LongShortRatioResponse> response = api.longShortRatio(symbol, period, limit, null, null);
        if (response.getData() != null) {
            return response.getData().stream().map(item -> {
                BinanceLongShortRatio binanceLongShortRatio = new BinanceLongShortRatio();
                binanceLongShortRatio.setSymbol(item.getSymbol());
                binanceLongShortRatio.setLongAccount(new BigDecimal(item.getLongAccount()));
                binanceLongShortRatio.setLongShortRatio(new BigDecimal(item.getLongShortRatio()));
                binanceLongShortRatio.setShortAccount(new BigDecimal(item.getShortAccount()));
                binanceLongShortRatio.setTimestamp(Long.parseLong(item.getTimestamp()));
                return binanceLongShortRatio;
            }).toList();
        }
        return new ArrayList<>();
    }

    private List<BinanceKline> parseKlines(KlineCandlestickDataResponse response, String symbol) {
        if (response == null) {
            log.warn("Binance K线接口返回为空");
            return Collections.emptyList();
        }

        List<BinanceKline> result = new ArrayList<>();
        response.forEach(item -> {
            BinanceKline kline = new BinanceKline();
            kline.setSymbol(symbol);
            kline.setOpenTime(Long.parseLong(item.get(0)));
            kline.setOpenPrice(new BigDecimal(item.get(1)));
            kline.setHighPrice(new BigDecimal(item.get(2)));
            kline.setLowPrice(new BigDecimal(item.get(3)));
            kline.setClosePrice(new BigDecimal(item.get(4)));
            kline.setVolume(new BigDecimal(item.get(5)));
            kline.setCloseTime(Long.parseLong(item.get(6)));
            kline.setQuoteAssetVolume(new BigDecimal(item.get(7)));
            kline.setNumberOfTrades(Long.parseLong(item.get(8)));
            kline.setTakerBuyBaseAssetVolume(new BigDecimal(item.get(9)));
            kline.setTakerBuyQuoteAssetVolume(new BigDecimal(item.get(10)));
            result.add(kline);
        });

        log.info("Binance K线解析完成: {} 条", result.size());
        return result;
    }

    private List<BinanceKline> getLatestKlines(List<BinanceKline> klines, Long limit) {
        if (klines.size() <= limit) {
            return klines;
        }
        return new ArrayList<>(klines.subList(klines.size() - limit.intValue(), klines.size()));
    }
}
