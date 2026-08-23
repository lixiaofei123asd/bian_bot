package com.lxf.bian_bot.config.strategy;

import java.util.LinkedHashMap;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(StrategyProperties.class)
public class StrategyConfiguration {

    private final StrategyProperties strategyProperties;

    /** 将配置文件中的策略配置转换成业务层使用的 Bean。 */
    @Bean
    public StrategyConfig strategyConfig() {
        LinkedHashMap<String, String> symbols = new LinkedHashMap<>();
        strategyProperties.getSymbols().forEach((symbol, strategy) ->
                symbols.put(symbol.toUpperCase(Locale.ROOT), strategy));
        return new StrategyConfig(symbols);
    }
}
