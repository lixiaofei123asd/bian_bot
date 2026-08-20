package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.KlineCandlestickDataResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.LongShortRatioResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Period;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceLongShortRatio;
import com.lxf.bian_bot.service.BinanceMarketService;
import java.math.BigDecimal;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BinanceMarketServiceImpl implements BinanceMarketService {

    private @Autowired DerivativesTradingUsdsFuturesRestApi api;

    @Override
    public List<BinanceKline> getKlines(String symbol, Interval interval, Long limit) {
        log.info("获取 Binance K线: symbol={}, interval={}, limit={}", symbol, interval, limit);
        ApiResponse<KlineCandlestickDataResponse> response = api.klineCandlestickData(symbol, interval, null, null, limit);
        return parseKlines(response.getData());
    }

    @Override
    public List<BinanceKline> getKlines(String symbol, Interval interval, Long startTime, Long endTime, Long limit) {
        log.info("获取 Binance K线: symbol={}, interval={}, startTime={}, endTime={}, limit={}", symbol, interval, startTime, endTime, limit);
        ApiResponse<KlineCandlestickDataResponse> response = api.klineCandlestickData(symbol, interval, startTime, endTime, limit);
        return parseKlines(response.getData());
    }

    @Override
    public BinanceKline getLatestKline(String symbol, Interval interval) {
        List<BinanceKline> klines = getKlines(symbol, interval, 1L);
        if (klines == null || klines.isEmpty()) {
            return null;
        }
        return klines.getFirst();
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

    private List<BinanceKline> parseKlines(KlineCandlestickDataResponse response) {
        if (response == null) {
            log.warn("Binance K线接口返回为空");
            return Collections.emptyList();
        }

        List<BinanceKline> result = new ArrayList<>();
        response.forEach(item -> {
            BinanceKline kline = new BinanceKline();

            /*
             * Binance K线数组：
             *
             * 0  开盘时间
             * 1  开盘价
             * 2  最高价
             * 3  最低价
             * 4  收盘价
             * 5  成交量
             * 6  收盘时间
             * 7  成交额
             * 8  成交笔数
             * 9  主动买入成交量
             * 10 主动买入成交额
             * 11 忽略
             */

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
}
