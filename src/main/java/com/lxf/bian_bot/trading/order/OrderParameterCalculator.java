package com.lxf.bian_bot.trading.order;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
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
     * 按交易规则修正市价单数量。
     *
     * 市价单本身不提交 price，因此 price 可以为 0。
     * 当调用方提供有效 price 时才校验最小名义价值；例如普通市价开仓可以传入当前价格，
     * 而 reduceOnly 市价平仓通常不需要也无法提前知道实际成交价格，可以直接传 0。
     */
    public double normalizeMarketQuantity(String symbol, double quantity, double price) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("下单数量必须大于 0：" + quantity);
        }
        if (price < 0) {
            throw new IllegalArgumentException("下单价格不能小于 0：" + price);
        }

        SymbolRule rule = getRule(symbol);
        BigDecimal quantityValue = normalizeQuantityValue(
                rule, symbol, BigDecimal.valueOf(quantity), true);

        // 市价平仓没有实际下单价格，price=0 时不能做名义价值校验。
        if (price > 0) {
            validateNotional(rule, symbol, quantityValue, BigDecimal.valueOf(price));
        }

        return quantityValue.doubleValue();
    }

    public double normalizeLimitQuantity(String symbol, double quantity, double price) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("下单数量必须大于 0：" + quantity);
        }
        if (price <= 0) {
            throw new IllegalArgumentException("下单价格必须大于 0：" + price);
        }

        SymbolRule rule = getRule(symbol);
        BigDecimal quantityValue = normalizeQuantityValue(rule, symbol, BigDecimal.valueOf(quantity), false);
        validateNotional(rule, symbol, quantityValue, BigDecimal.valueOf(price));
        return quantityValue.doubleValue();
    }

    public double normalizeEntryPrice(String symbol, double price, boolean buy) {
        return normalizePrice(symbol, price, buy ? RoundingMode.DOWN : RoundingMode.UP);
    }

    public double normalizeTriggerPrice(String symbol, double price, Side entrySide, boolean takeProfit) {
        RoundingMode roundingMode;
        if (Side.BUY.equals(entrySide)) {
            roundingMode = takeProfit ? RoundingMode.UP : RoundingMode.DOWN;
        } else {
            roundingMode = takeProfit ? RoundingMode.DOWN : RoundingMode.UP;
        }
        return normalizePrice(symbol, price, roundingMode);
    }

    private double normalizePrice(String symbol, double price, RoundingMode roundingMode) {
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
                .divide(tickSize, 0, roundingMode)
                .multiply(tickSize);

        validatePrice(rule, symbol, normalizedPrice);
        return normalizedPrice.doubleValue();
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

        quantity = normalizeQuantityValue(rule, symbol, quantity, marketOrder);
        validateNotional(rule, symbol, quantity, priceValue);
        return quantity.doubleValue();
    }

    private BigDecimal normalizeQuantityValue(SymbolRule rule, String symbol, BigDecimal quantity, boolean marketOrder) {
        BigDecimal minQty = marketOrder && rule.getMarketMinQty() != null ? rule.getMarketMinQty() : rule.getMinQty();
        BigDecimal maxQty = marketOrder && rule.getMarketMaxQty() != null ? rule.getMarketMaxQty() : rule.getMaxQty();
        BigDecimal stepSize = marketOrder && rule.getMarketStepSize() != null ? rule.getMarketStepSize() : rule.getStepSize();

        if (stepSize == null || stepSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("交易规则缺少有效的数量步长：" + symbol);
        }

        quantity = quantity.divide(stepSize, 0, RoundingMode.DOWN).multiply(stepSize);

        if (minQty != null && quantity.compareTo(minQty) < 0) {
            quantity = minQty;
        }

        if (maxQty != null && quantity.compareTo(maxQty) > 0) {
            throw new IllegalArgumentException(
                    String.format("%s 下单数量超过最大值：quantity=%s, maxQty=%s",
                            symbol, quantity.stripTrailingZeros().toPlainString(),
                            maxQty.stripTrailingZeros().toPlainString()));
        }

        return quantity;
    }

    private void validateNotional(SymbolRule rule, String symbol, BigDecimal quantity, BigDecimal price) {
        BigDecimal minNotional = rule.getMinNotional();
        if (minNotional == null || minNotional.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal notional = quantity.multiply(price);
        if (notional.compareTo(minNotional) < 0) {
            throw new IllegalArgumentException(
                    String.format("%s 下单名义价值不足：quantity=%s, price=%s, notional=%s, minNotional=%s",
                            symbol,
                            quantity.stripTrailingZeros().toPlainString(),
                            price.stripTrailingZeros().toPlainString(),
                            notional.stripTrailingZeros().toPlainString(),
                            minNotional.stripTrailingZeros().toPlainString()));
        }
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
