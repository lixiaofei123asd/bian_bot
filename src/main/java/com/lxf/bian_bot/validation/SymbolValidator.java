package com.lxf.bian_bot.validation;

import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.StrategyRegistry;
import com.lxf.bian_bot.config.strategy.StrategyConfig;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SymbolValidator implements ConstraintValidator<Symbol, String> {

    private final StrategyConfig strategyConfig;
    private final StrategyRegistry strategyRegistry;

    public SymbolValidator(StrategyConfig strategyConfig, StrategyRegistry strategyRegistry) {
        this.strategyConfig = strategyConfig;
        this.strategyRegistry = strategyRegistry;
    }

    @Override
    public boolean isValid(String symbol, ConstraintValidatorContext context) {
        if (symbol == null || symbol.isBlank()) {
            this.alterMessage(context, "币种不能为空");
            return false;
        }

        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);

        // 根据币种获取唯一策略
        String strategyName = strategyConfig.getStrategyName(normalizedSymbol);
        if (strategyName == null || strategyName.isBlank()) {
            this.alterMessage(context, normalizedSymbol + "未配置交易策略");
            return false;
        }

        Strategy strategy = strategyRegistry.getStrategy(strategyName);
        if (strategy == null) {
            this.alterMessage(context, normalizedSymbol + "配置了不存在的策略: " + strategyName);
            return false;
        }

        return true;
    }

    private void alterMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
