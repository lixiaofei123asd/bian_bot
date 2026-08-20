package com.lxf.bian_bot.indicator.impl;

import com.lxf.bian_bot.indicator.Indicator;
import com.lxf.bian_bot.indicator.Strategy;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Ema99Indicator implements Indicator {


    @Override
    public Strategy name() {
        return Strategy.EMA99;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void calculate(List<BinanceKline> klines, BinanceMarketAnalysis analysis) {
        System.out.println("Ema99Indicator分析");
    }
}
