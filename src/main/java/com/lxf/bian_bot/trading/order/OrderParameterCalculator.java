package com.lxf.bian_bot.trading.order;

import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.model.SymbolRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class OrderParameterCalculator {

    private final SymbolRuleCache symbolRuleCache;

    public OrderParameterCalculator(SymbolRuleCache symbolRuleCache) {
        this.symbolRuleCache = symbolRuleCache;
    }

    public double calculateMarketQuantity(String symbol, double usdtAmount, double price) {
        return calculateQuantity(symbol, usdtAmount, price, true);
    }

    public double calculateLimitQuantity(String symbol, double usdtAmount, double price) {
        return calculateQuantity(symbol, usdtAmount, price, false);
    }

    private double calculateQuantity(String symbol, double usdtAmount, double price, boolean marketOrder) {
        if (usdtAmount <= 0) {
            throw new IllegalArgumentException("下单金额必须大于 0：" + usdtAmount);
        }
        if (price <= 0) {
            throw new IllegalArgumentException("下单价格必须大于 0：" + price);
        }

        SymbolRule rule = symbolRuleCache.get(symbol);
        if (rule == null) {
            throw new IllegalArgumentException("未找到交易规则：" + symbol);
        }

        BigDecimal quantity = BigDecimal.valueOf(usdtAmount)
                .divide(BigDecimal.valueOf(price), 20, RoundingMode.DOWN);

        BigDecimal minQty = marketOrder && rule.getMarketMinQty() != null
                ? rule.getMarketMinQty() : rule.getMinQty();
        BigDecimal maxQty = marketOrder && rule.getMarketMaxQty() != null
                ? rule.getMarketMaxQty() : rule.getMaxQty();
        BigDecimal stepSize = marketOrder && rule.getMarketStepSize() != null
                ? rule.getMarketStepSize() : rule.getStepSize();

        if (stepSize == null || stepSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易规则缺少有效的数量步长：" + symbol);
        }

        quantity = quantity.divide(stepSize, 0, RoundingMode.DOWN).multiply(stepSize);

        if (minQty != null && quantity.compareTo(minQty) < 0) {
            throw new IllegalArgumentException(
                    String.format("%s 下单数量不足：quantity=%s, minQty=%s, usdtAmount=%s, price=%s",
                            symbol, quantity.stripTrailingZeros().toPlainString(),
                            minQty.stripTrailingZeros().toPlainString(), usdtAmount, price));
        }

        if (maxQty != null && quantity.compareTo(maxQty) > 0) {
            throw new IllegalArgumentException(
                    String.format("%s 下单数量超过最大值：quantity=%s, maxQty=%s",
                            symbol, quantity.stripTrailingZeros().toPlainString(),
                            maxQty.stripTrailingZeros().toPlainString()));
        }

        return quantity.doubleValue();
    }
}
