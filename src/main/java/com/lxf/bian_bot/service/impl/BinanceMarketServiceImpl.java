package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.DerivativesTradingUsdsFuturesRestApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.KlineCandlestickDataResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.MarkPriceResponse1;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.OrderBookResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.RecentTradesListResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.SymbolOrderBookTickerResponse1;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.SymbolPriceTickerV2Response1;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Ticker24hrPriceChangeStatisticsResponse1;
import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.model.Binance24hTicker;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarkPrice;
import com.lxf.bian_bot.model.BinanceOrderBook;
import com.lxf.bian_bot.model.BinanceOrderBookLevel;
import com.lxf.bian_bot.model.BinancePriceTicker;
import com.lxf.bian_bot.model.BinanceRecentTrade;
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
        ApiResponse<KlineCandlestickDataResponse> response =
                api.klineCandlestickData(symbol, interval, null, null, limit);
        List<BinanceKline> klines = parseKlines(response.getData(), symbol);
        klineCache.put(symbol, cacheInterval, klines);
        return klines;
    }

    @Override
    public List<BinanceKline> refreshLatestKlines(String symbol, Interval interval, Long limit) {
        log.info("强制获取 Binance 最新 K线: symbol={}, interval={}, limit={}", symbol, interval, limit);
        ApiResponse<KlineCandlestickDataResponse> response = api.klineCandlestickData(symbol, interval, null, null, limit);
        return parseKlines(response.getData(), symbol);
    }

    @Override
    public BinancePriceTicker getPrice(String symbol) {
        SymbolPriceTickerV2Response1 data = api.symbolPriceTickerV2(symbol).getData().getSymbolPriceTickerV2Response1();

        BinancePriceTicker result = new BinancePriceTicker();
        result.setSymbol(data.getSymbol());
        result.setPrice(toBigDecimal(data.getPrice()));
        result.setTime(data.getTime());
        return result;
    }

    @Override
    public BinanceMarkPrice getMarkPrice(String symbol) {
        MarkPriceResponse1 data = api.markPrice(symbol).getData().getMarkPriceResponse1();

        BinanceMarkPrice result = new BinanceMarkPrice();
        result.setSymbol(data.getSymbol());
        result.setMarkPrice(toBigDecimal(data.getMarkPrice()));
        result.setIndexPrice(toBigDecimal(data.getIndexPrice()));
        result.setEstimatedSettlePrice(toBigDecimal(data.getEstimatedSettlePrice()));
        result.setLastFundingRate(toBigDecimal(data.getLastFundingRate()));
        result.setInterestRate(toBigDecimal(data.getInterestRate()));
        result.setNextFundingTime(data.getNextFundingTime());
        result.setTime(data.getTime());
        return result;
    }

    @Override
    public Binance24hTicker get24hTicker(String symbol) {
        Ticker24hrPriceChangeStatisticsResponse1 data =
                api.ticker24hrPriceChangeStatistics(symbol).getData().getTicker24hrPriceChangeStatisticsResponse1();

        Binance24hTicker result = new Binance24hTicker();
        result.setSymbol(data.getSymbol());
        result.setPriceChange(toBigDecimal(data.getPriceChange()));
        result.setPriceChangePercent(toBigDecimal(data.getPriceChangePercent()));
        result.setWeightedAvgPrice(toBigDecimal(data.getWeightedAvgPrice()));
        result.setLastPrice(toBigDecimal(data.getLastPrice()));
        result.setLastQty(toBigDecimal(data.getLastQty()));
        result.setOpenPrice(toBigDecimal(data.getOpenPrice()));
        result.setHighPrice(toBigDecimal(data.getHighPrice()));
        result.setLowPrice(toBigDecimal(data.getLowPrice()));
        result.setVolume(toBigDecimal(data.getVolume()));
        result.setQuoteVolume(toBigDecimal(data.getQuoteVolume()));
        result.setOpenTime(data.getOpenTime());
        result.setCloseTime(data.getCloseTime());
        result.setFirstId(data.getFirstId());
        result.setLastId(data.getLastId());
        result.setCount(data.getCount());
        return result;
    }

    @Override
    public BinanceBookTicker getBookTicker(String symbol) {
        SymbolOrderBookTickerResponse1 data =
                api.symbolOrderBookTicker(symbol).getData().getSymbolOrderBookTickerResponse1();

        BinanceBookTicker result = new BinanceBookTicker();
        result.setSymbol(data.getSymbol());
        result.setBidPrice(toBigDecimal(data.getBidPrice()));
        result.setBidQuantity(toBigDecimal(data.getBidQty()));
        result.setAskPrice(toBigDecimal(data.getAskPrice()));
        result.setAskQuantity(toBigDecimal(data.getAskQty()));
        result.setEventTime(data.getTime());
        return result;
    }

    @Override
    public BinanceOrderBook getOrderBook(String symbol, Long limit) {
        ApiResponse<OrderBookResponse> response = api.orderBook(symbol, limit);
        OrderBookResponse data = response.getData();

        BinanceOrderBook result = new BinanceOrderBook();
        result.setLastUpdateId(data.getLastUpdateId());
        result.setEventTime(data.getE());
        result.setTransactionTime(data.getT());
        List<BinanceOrderBookLevel> bids = data.getBids().stream()
                .map(bid -> {
                    BinanceOrderBookLevel level = new BinanceOrderBookLevel();
                    level.setPrice(new BigDecimal(bid.get(0)));
                    level.setQuantity(new BigDecimal(bid.get(1)));
                    return level;
                })
                .toList();
        List<BinanceOrderBookLevel> asks = data.getAsks().stream()
                .map(ask -> {
                    BinanceOrderBookLevel level = new BinanceOrderBookLevel();
                    level.setPrice(new BigDecimal(ask.get(0)));
                    level.setQuantity(new BigDecimal(ask.get(1)));
                    return level;
                })
                .toList();
        result.setBids(bids);
        result.setAsks(asks);
        return result;
    }

    @Override
    public List<BinanceRecentTrade> getRecentTrades(String symbol, Long limit) {
        RecentTradesListResponse data = api.recentTradesList(symbol, limit).getData();
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        List<BinanceRecentTrade> result = new ArrayList<>(data.size());
        data.forEach(item -> {
            BinanceRecentTrade trade = new BinanceRecentTrade();
            trade.setId(item.getId());
            trade.setPrice(toBigDecimal(item.getPrice()));
            trade.setQuantity(toBigDecimal(item.getQty()));
            trade.setQuoteQuantity(toBigDecimal(item.getQuoteQty()));
            trade.setTime(item.getTime());
            trade.setBuyerMaker(item.getIsBuyerMaker());
            result.add(trade);
        });
        return result;
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

    private BigDecimal toBigDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private List<BinanceKline> getLatestKlines(List<BinanceKline> klines, Long limit) {
        if (klines.size() <= limit) {
            return klines;
        }
        return new ArrayList<>(klines.subList(klines.size() - limit.intValue(), klines.size()));
    }
}
