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

    /**
     * 按交易对价格规则修正价格。
     * 价格必须符合 PRICE_FILTER 的 tickSize。
     */
    public double normalizePrice(String symbol, double price) {
        if (price <= 0) {
            throw new IllegalArgumentException("价格必须大于 0：" + price);
        }

        SymbolRule rule = getRule(symbol);
        BigDecimal priceValue = BigDecimal.valueOf(price);
        BigDecimal tickSize = rule.getTickSize();
        if (tickSize == null || tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易规则缺少有效的价格步长：" + symbol);
        }

        BigDecimal normalizedPrice = priceValue
                .divide(tickSize, 0, RoundingMode.DOWN)
                .multiply(tickSize);

        if (rule.getMinPrice() != null && normalizedPrice.compareTo(rule.getMinPrice()) < 0) {
            throw new IllegalArgumentException(
                    String.format("%s 价格低于最小价格：price=%s, minPrice=%s",
                            symbol, normalizedPrice.stripTrailingZeros().toPlainString(),
                            rule.getMinPrice().stripTrailingZeros().toPlainString()));
        }

        if (rule.getMaxPrice() != null && normalizedPrice.compareTo(rule.getMaxPrice()) > 0) {
            throw new IllegalArgumentException(
                    String.format("%s 价格超过最大价格：price=%s, maxPrice=%s",
                            symbol, normalizedPrice.stripTrailingZeros().toPlainString(),
                            rule.getMaxPrice().stripTrailingZeros().toPlainString()));
        }

        return normalizedPrice.doubleValue();
    }

    /**
     * BUY 开仓价格向下取整，SELL 开仓价格向上取整，避免因为 tickSize 修正后价格方向发生不利变化。
     */
    public double normalizeEntryPrice(String symbol, double price, boolean buy) {
        if (price <= 0) {
            throw new IllegalArgumentException("价格必须大于 0：" + price);
        }

        SymbolRule rule = getRule(symbol);
        BigDecimal priceValue = BigDecimal.valueOf(price);
        BigDecimal tickSize = rule.getTickSize();
        if (tickSize == null || tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易规则缺少有效的价格步长：" + symbol);
        }

        RoundingMode roundingMode = buy ? RoundingMode.DOWN : RoundingMode.UP;
        BigDecimal normalizedPrice = priceValue
                .divide(tickSize, 0, roundingMode)
                .multiply(tickSize);

        validatePrice(rule, symbol, normalizedPrice);
        return normalizedPrice.doubleValue();
    }

    /**
     * TP/SL 价格按照 Binance PRICE_FILTER 修正。
     * 这里统一向下取整，确保提交给 Binance 的价格一定落在 tickSize 上。
     */
    public double normalizeTriggerPrice(String symbol, double price) {
        return normalizePrice(symbol, price);
    }

    private double calculateQuantity(String symbol, double usdtAmount, double price, boolean marketOrder) {
        if (usdtAmount <= 0) {
            throw new IllegalArgumentException("下单金额必须大于 0：" + usdtAmount);
        }
        if (price <= 0) {
            throw new IllegalArgumentException("下单价格必须大于 0：" + price);
        }

        SymbolRule rule = getRule(symbol);
        BigDecimal priceValue = BigDecimal.valueOf(price);
        BigDecimal quantity = BigDecimal.valueOf(usdtAmount)
                .divide(priceValue, 20, RoundingMode.DOWN);

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
            quantity = minQty;
        }

        BigDecimal minNotional = rule.getMinNotional();
        if (minNotional != null && minNotional.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal notional = quantity.multiply(priceValue);
            if (notional.compareTo(minNotional) < 0) {
                BigDecimal requiredQuantity = minNotional.divide(priceValue, 20, RoundingMode.UP);
                quantity = requiredQuantity.divide(stepSize, 0, RoundingMode.CEILING).multiply(stepSize);
            }
        }

        if (minQty != null && quantity.compareTo(minQty) < 0) {
            quantity = minQty;
        }

        if (maxQty != null && quantity.compareTo(maxQty) > 0) {
            throw new IllegalArgumentException(
                    String.format("%s 下单数量超过最大值：quantity=%s, maxQty=%s",
                            symbol, quantity.stripTrailingZeros().toPlainString(),
                            maxQty.stripTrailingZeros().toPlainString()));
        }

        BigDecimal finalNotional = quantity.multiply(priceValue);
        if (minNotional != null && finalNotional.compareTo(minNotional) < 0) {
            throw new IllegalArgumentException(
                    String.format("%s 下单名义价值不足：quantity=%s, price=%s, notional=%s, minNotional=%s",
                            symbol, quantity.stripTrailingZeros().toPlainString(),
                            priceValue.stripTrailingZeros().toPlainString(),
                            finalNotional.stripTrailingZeros().toPlainString(),
                            minNotional.stripTrailingZeros().toPlainString()));
        }

        return quantity.doubleValue();
    }

    private SymbolRule getRule(String symbol) {
        SymbolRule rule = symbolRuleCache.get(symbol);
        if (rule == null) {
            throw new IllegalArgumentException("未找到交易规则：" + symbol);
        }
        return rule;
    }

    private void validatePrice(SymbolRule rule, String symbol, BigDecimal price) {
        if (rule.getMinPrice() != null && price.compareTo(rule.getMinPrice()) < 0) {
            throw new IllegalArgumentException(
                    String.format("%s 价格低于最小价格：price=%s, minPrice=%s",
                            symbol, price.stripTrailingZeros().toPlainString(),
                            rule.getMinPrice().stripTrailingZeros().toPlainString()));
        }
        if (rule.getMaxPrice() != null && price.compareTo(rule.getMaxPrice()) > 0) {
            throw new IllegalArgumentException(
                    String.format("%s 价格超过最大价格：price=%s, maxPrice=%s",
                            symbol, price.stripTrailingZeros().toPlainString(),
                            rule.getMaxPrice().stripTrailingZeros().toPlainString()));
        }
    }
}
