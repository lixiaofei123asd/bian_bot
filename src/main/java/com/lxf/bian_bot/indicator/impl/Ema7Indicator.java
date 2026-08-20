package com.lxf.bian_bot.indicator.impl;

import com.lxf.bian_bot.indicator.Indicator;
import com.lxf.bian_bot.indicator.Strategy;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Ema7Indicator implements Indicator {
    @Override
    public Strategy name() {
        return Strategy.EMA7;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void calculate(List<BinanceKline> klines, BinanceMarketAnalysis analysis) {
        System.out.println("Ema7Indicator分析");
    }
}
