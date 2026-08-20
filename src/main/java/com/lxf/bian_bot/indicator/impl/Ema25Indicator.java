package com.lxf.bian_bot.indicator.impl;

import com.lxf.bian_bot.indicator.Indicator;
import com.lxf.bian_bot.indicator.Strategy;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Ema25Indicator implements Indicator {
    @Override
    public Strategy name() {
        return Strategy.EMA25;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void calculate(List<BinanceKline> klines, BinanceMarketAnalysis analysis) {
        System.out.println("Ema25Indicator分析");

        BigDecimal ema7 = new BigDecimal("123");

        analysis.setEma7(ema7);
    }
}
