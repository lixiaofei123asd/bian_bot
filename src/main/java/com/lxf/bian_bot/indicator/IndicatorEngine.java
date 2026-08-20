package com.lxf.bian_bot.indicator;

import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IndicatorEngine {

    private final Map<Strategy, Indicator> indicators;

    public IndicatorEngine(List<Indicator> indicators) {
        this.indicators = indicators.stream().collect(Collectors.toMap(Indicator::name, Function.identity()));
        log.info("初始化指标数量: {}", this.indicators.size());
    }

    /**
     * 执行所有启用的指标
     */
    public void calculate(List<BinanceKline> klines, BinanceMarketAnalysis analysis) {

        for (Indicator indicator : indicators.values()) {
            if (!indicator.enabled()) {
                log.debug("指标未启用，跳过: {}", indicator.name());
                continue;
            }

            log.debug("开始计算指标: {}", indicator.name());
            indicator.calculate(klines, analysis);
        }
    }
}
