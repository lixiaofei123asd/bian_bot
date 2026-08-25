package com.lxf.bian_bot.trading.order;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.config.SymbolRule;
import com.lxf.bian_bot.config.SymbolRuleCache;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class OrderParameterCalculator {

    private final SymbolRuleCache symbolRuleCache;

    public OrderParameterCalculator(SymbolRuleCache symbolRuleCache) {
        this.symbolRuleCache = symbolRuleCache;
    }

    public BigDecimal calculateMarketQuantity(String symbol, BigDecimal positionNotional, BigDecimal price) {
        return calculateQuantity(symbol, positionNotional, price, true);
    }

    public BigDecimal calculateLimitQuantity(String symbol, BigDecimal positionNotional, BigDecimal price) {
        return calculateQuantity(symbol, positionNotional, price, false);
    }

    public BigDecimal normalizeMarketQuantity(String symbol, BigDecimal quantity, BigDecimal price) {
        requirePositive(quantity, "下单数量");
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("下单价格不能小于 0：" + price);
        }
        SymbolRule rule = getRule(symbol);
        BigDecimal quantityValue = normalizeQuantityValue(rule, symbol, quantity, true);
        if (price.compareTo(BigDecimal.ZERO) > 0) validateNotional(rule, symbol, quantityValue, price);
        return quantityValue;
    }

    public BigDecimal normalizeLimitQuantity(String symbol, BigDecimal quantity, BigDecimal price) {
        requirePositive(quantity, "下单数量");
        requirePositive(price, "下单价格");
        SymbolRule rule = getRule(symbol);
        BigDecimal quantityValue = normalizeQuantityValue(rule, symbol, quantity, false);
        validateNotional(rule, symbol, quantityValue, price);
        return quantityValue;
    }

    public BigDecimal normalizeEntryPrice(String symbol, BigDecimal price, boolean buy) {
        return normalizePrice(symbol, price, buy ? RoundingMode.DOWN : RoundingMode.UP);
    }

    public BigDecimal normalizeTriggerPrice(String symbol, BigDecimal price, Side entrySide, boolean takeProfit) {
        RoundingMode roundingMode;
        if (Side.BUY.equals(entrySide)) {
            roundingMode = takeProfit ? RoundingMode.UP : RoundingMode.DOWN;
        } else {
            roundingMode = takeProfit ? RoundingMode.DOWN : RoundingMode.UP;
        }
        return normalizePrice(symbol, price, roundingMode);
    }

    private BigDecimal normalizePrice(String symbol, BigDecimal price, RoundingMode roundingMode) {
        requirePositive(price, "价格");
        SymbolRule rule = getRule(symbol);
        BigDecimal tickSize = rule.getTickSize();
        if (tickSize == null || tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易规则缺少有效的价格步长：" + symbol);
        }
        BigDecimal normalizedPrice = price.divide(tickSize, 0, roundingMode).multiply(tickSize);
        validatePrice(rule, symbol, normalizedPrice);
        return normalizedPrice;
    }

    private BigDecimal calculateQuantity(String symbol, BigDecimal positionNotional,
                                         BigDecimal price, boolean marketOrder) {
        requirePositive(positionNotional, "仓位名义价值");
        requirePositive(price, "下单价格");
        SymbolRule rule = getRule(symbol);
        BigDecimal quantity = positionNotional.divide(price, 20, RoundingMode.DOWN);
        quantity = normalizeQuantityValue(rule, symbol, quantity, marketOrder);
        validateNotional(rule, symbol, quantity, price);
        return quantity;
    }

    private BigDecimal normalizeQuantityValue(SymbolRule rule, String symbol,
                                              BigDecimal quantity, boolean marketOrder) {
        BigDecimal minQty = marketOrder && rule.getMarketMinQty() != null ? rule.getMarketMinQty() : rule.getMinQty();
        BigDecimal maxQty = marketOrder && rule.getMarketMaxQty() != null ? rule.getMarketMaxQty() : rule.getMaxQty();
        BigDecimal stepSize = marketOrder && rule.getMarketStepSize() != null ? rule.getMarketStepSize() : rule.getStepSize();
        if (stepSize == null || stepSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易规则缺少有效的数量步长：" + symbol);
        }
        quantity = quantity.divide(stepSize, 0, RoundingMode.DOWN).multiply(stepSize);
        if (minQty != null && quantity.compareTo(minQty) < 0) quantity = minQty;
        if (maxQty != null && quantity.compareTo(maxQty) > 0) {
            throw new IllegalArgumentException(String.format("%s 下单数量超过最大值：quantity=%s, maxQty=%s",
                    symbol, quantity.stripTrailingZeros().toPlainString(), maxQty.stripTrailingZeros().toPlainString()));
        }
        return quantity;
    }

    private void validateNotional(SymbolRule rule, String symbol, BigDecimal quantity, BigDecimal price) {
        BigDecimal minNotional = rule.getMinNotional();
        if (minNotional == null || minNotional.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal notional = quantity.multiply(price);
        if (notional.compareTo(minNotional) < 0) {
            throw new IllegalArgumentException(String.format("%s 下单名义价值不足：quantity=%s, price=%s, notional=%s, minNotional=%s",
                    symbol, quantity.stripTrailingZeros().toPlainString(), price.stripTrailingZeros().toPlainString(),
                    notional.stripTrailingZeros().toPlainString(), minNotional.stripTrailingZeros().toPlainString()));
        }
    }

    private SymbolRule getRule(String symbol) {
        SymbolRule rule = symbolRuleCache.get(symbol);
        if (rule == null) throw new IllegalArgumentException("未找到交易规则：" + symbol);
        return rule;
    }

    private void validatePrice(SymbolRule rule, String symbol, BigDecimal price) {
        if (rule.getMinPrice() != null && price.compareTo(rule.getMinPrice()) < 0) {
            throw new IllegalArgumentException(String.format("%s 价格低于最小价格：price=%s, minPrice=%s",
                    symbol, price.stripTrailingZeros().toPlainString(), rule.getMinPrice().stripTrailingZeros().toPlainString()));
        }
        if (rule.getMaxPrice() != null && price.compareTo(rule.getMaxPrice()) > 0) {
            throw new IllegalArgumentException(String.format("%s 价格超过最大价格：price=%s, maxPrice=%s",
                    symbol, price.stripTrailingZeros().toPlainString(), rule.getMaxPrice().stripTrailingZeros().toPlainString()));
        }
    }

    private void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + "必须大于 0：" + value);
        }
    }
}
