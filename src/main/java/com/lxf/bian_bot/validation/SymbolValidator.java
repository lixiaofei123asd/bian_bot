package com.lxf.bian_bot.validation;

import com.lxf.bian_bot.analysis.Strategy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;

public class SymbolValidator implements ConstraintValidator<Symbol, String> {

    @Override
    public boolean isValid(String symbol, ConstraintValidatorContext context) {
// 根据币种获取唯一策略
        String strategyName = strategyConfig.getStrategyName(symbol);
        if (strategyName == null || strategyName.isBlank()) {
            this.alterMessage(context, symbol + "未配置交易策略");
            return false;
        }

        Strategy strategy = strategies.get(strategyName.trim().toUpperCase(Locale.ROOT));
        if (strategy == null) {
            this.alterMessage(context, symbol + "配置了不存在的策略" + strategyName);
            return false;
        }
        return false;
    }

    private void alterMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
