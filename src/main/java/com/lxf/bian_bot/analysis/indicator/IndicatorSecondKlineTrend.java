package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.IndicatorResult;
import com.lxf.bian_bot.analysis.MarketDataProvider;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceSecondKline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 基于最近 1 秒 K 线判断极短线价格趋势。
 *
 * <p>设计目标不是预测，而是过滤 1 秒级别的频繁反转，只在短线方向足够明确时返回 UP/DOWN。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndicatorSecondKlineTrend implements Indicator {

    /** 参与判断的 1 秒 K 线数量 */
    private static final int KLINE_COUNT = 8;

    /** 至少需要多少根 K 线支持同一方向 */
    private static final int MIN_DIRECTION_COUNT = 5;

    /** 方向占比最低要求 */
    private static final double MIN_DIRECTION_RATIO = 0.625;

    /** 最后几根 K 线必须保持方向 */
    private static final int RECENT_DIRECTION_COUNT = 2;

    /** 允许的最大反转次数 */
    private static final int MAX_REVERSALS = 3;

    /** 最小总体价格变化比例，0.03% */
    private static final BigDecimal MIN_PRICE_CHANGE_RATIO = new BigDecimal("0.0003");

    private final MarketDataProvider marketDataProvider;

    @Override
    public String getName() {
        return "SECOND_KLINE_TREND";
    }

    @Override
    public IndicatorResult calculate(String symbol) {
        List<BinanceSecondKline> klines =
                marketDataProvider.getSecondKlinesIncludingCurrent(symbol, KLINE_COUNT);

        if (klines == null || klines.size() < KLINE_COUNT) {
            return neutralResult();
        }

        BinanceSecondKline first = klines.get(0);
        BinanceSecondKline last = klines.get(klines.size() - 1);

        if (first.getOpenPrice() == null || last.getClosePrice() == null
                || first.getOpenPrice().signum() <= 0 || last.getClosePrice().signum() <= 0) {
            return neutralResult();
        }

        int upCount = 0;
        int downCount = 0;
        int reversals = 0;
        IndicatorSignal previousDirection = IndicatorSignal.NEUTRAL;

        for (BinanceSecondKline kline : klines) {
            if (kline.getOpenPrice() == null || kline.getClosePrice() == null) {
                continue;
            }

            int compare = kline.getClosePrice().compareTo(kline.getOpenPrice());
            IndicatorSignal direction;
            if (compare > 0) {
                direction = IndicatorSignal.UP;
                upCount++;
            } else if (compare < 0) {
                direction = IndicatorSignal.DOWN;
                downCount++;
            } else {
                continue;
            }

            if (previousDirection != IndicatorSignal.NEUTRAL && direction != previousDirection) {
                reversals++;
            }
            previousDirection = direction;
        }

        int totalDirectional = upCount + downCount;
        if (totalDirectional == 0 || reversals > MAX_REVERSALS) {
            return neutralResult();
        }

        BigDecimal priceChangeRatio = last.getClosePrice()
                .subtract(first.getOpenPrice())
                .divide(first.getOpenPrice(), 8, RoundingMode.HALF_UP)
                .abs();

        if (priceChangeRatio.compareTo(MIN_PRICE_CHANGE_RATIO) < 0) {
            return neutralResult();
        }

        IndicatorSignal overallDirection =
                last.getClosePrice().compareTo(first.getOpenPrice()) > 0
                        ? IndicatorSignal.UP : IndicatorSignal.DOWN;

        int directionCount = overallDirection == IndicatorSignal.UP ? upCount : downCount;
        double ratio = (double) directionCount / totalDirectional;

        if (directionCount < MIN_DIRECTION_COUNT || ratio < MIN_DIRECTION_RATIO) {
            return neutralResult();
        }

        if (!hasRecentDirection(klines, overallDirection)) {
            return neutralResult();
        }

        log.debug("1秒K线趋势: symbol={}, direction={}, upCount={}, downCount={}, reversals={}, priceChange={}%",
                symbol,
                overallDirection,
                upCount,
                downCount,
                reversals,
                priceChangeRatio.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP));

        return IndicatorResult.builder()
                .indicatorName(getName())
                .signal(overallDirection)
                .confidence((int) Math.round(ratio * 100))
                .reason(String.format(
                        "最近%d根1秒K线：上涨%d根、下跌%d根、反转%d次，整体价格变化%s%%",
                        KLINE_COUNT,
                        upCount,
                        downCount,
                        reversals,
                        priceChangeRatio.multiply(BigDecimal.valueOf(100))
                                .setScale(4, RoundingMode.HALF_UP)))
                .build();
    }

    private IndicatorResult neutralResult() {
        return IndicatorResult.builder()
                .indicatorName(getName())
                .signal(IndicatorSignal.NEUTRAL)
                .confidence(0)
                .build();
    }

    private boolean hasRecentDirection(List<BinanceSecondKline> klines, IndicatorSignal expected) {
        int start = Math.max(0, klines.size() - RECENT_DIRECTION_COUNT);

        for (int i = start; i < klines.size(); i++) {
            BinanceSecondKline kline = klines.get(i);
            if (kline.getOpenPrice() == null || kline.getClosePrice() == null) {
                return false;
            }

            int compare = kline.getClosePrice().compareTo(kline.getOpenPrice());
            IndicatorSignal actual = compare > 0
                    ? IndicatorSignal.UP
                    : compare < 0 ? IndicatorSignal.DOWN : IndicatorSignal.NEUTRAL;

            if (actual != expected) {
                return false;
            }
        }

        return true;
    }
}
