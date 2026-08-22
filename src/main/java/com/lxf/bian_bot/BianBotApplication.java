package com.lxf.bian_bot;

import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.config.StrategyProperties;
import com.lxf.bian_bot.config.TradingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({BinanceProperties.class, StrategyProperties.class, TradingProperties.class})
public class BianBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(BianBotApplication.class, args);
    }
}
