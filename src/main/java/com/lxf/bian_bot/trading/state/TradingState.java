package com.lxf.bian_bot.trading.state;

import com.lxf.bian_bot.thirdparty.binance.model.AccountInfo;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.model.ProtectionInfo;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;

/** 全局交易状态中心。 */
@Getter
@Component
public class TradingState {

    private static final ZoneId ACCOUNT_ZONE = ZoneId.of("Asia/Shanghai");

    private volatile AccountInfo accountInfo;
    private final Map<String, PositionInfo> positions = new ConcurrentHashMap<>();
    private final Map<String, ProtectionInfo> protections = new ConcurrentHashMap<>();
    private final Map<Long, OrderInfo> orders = new ConcurrentHashMap<>();
    private final Map<String, Boolean> orderSubmitting = new ConcurrentHashMap<>();
    private final Map<String, TradingExecutionState> executionStates = new ConcurrentHashMap<>();

    private volatile LocalDate realizedPnlDate = LocalDate.now(ACCOUNT_ZONE);
    private volatile double todayRealizedPnl;

    public void updateAccount(AccountInfo accountInfo) { if (accountInfo != null) this.accountInfo = accountInfo; }

    public void updatePosition(PositionInfo positionInfo) {
        if (positionInfo != null && positionInfo.getSymbol() != null) positions.put(positionInfo.getSymbol(), positionInfo);
    }

    public void updateProtection(ProtectionInfo protectionInfo) {
        if (protectionInfo == null || protectionInfo.getSymbol() == null || protectionInfo.getSymbol().isBlank()) return;
        protections.put(protectionInfo.getSymbol(), protectionInfo);
    }

    public ProtectionInfo getProtection(String symbol) { return protections.get(symbol); }

    public void updateTakeProfit(String symbol, double price, long algoId) {
        ProtectionInfo protection = protections.computeIfAbsent(symbol,
                key -> ProtectionInfo.builder().symbol(key).build());
        protection.setTakeProfitPrice(price);
        protection.setTakeProfitAlgoId(algoId);
    }

    public void updateStopLoss(String symbol, double price, long algoId) {
        ProtectionInfo protection = protections.computeIfAbsent(symbol,
                key -> ProtectionInfo.builder().symbol(key).build());
        protection.setStopLossPrice(price);
        protection.setStopLossAlgoId(algoId);
    }

    public void clearTakeProfit(String symbol) {
        ProtectionInfo protection = protections.get(symbol);
        if (protection == null) return;
        protection.setTakeProfitPrice(0D);
        protection.setTakeProfitAlgoId(0L);
        removeProtectionIfEmpty(symbol, protection);
    }

    public void clearStopLoss(String symbol) {
        ProtectionInfo protection = protections.get(symbol);
        if (protection == null) return;
        protection.setStopLossPrice(0D);
        protection.setStopLossAlgoId(0L);
        removeProtectionIfEmpty(symbol, protection);
    }

    public void clearProtection(String symbol) { if (symbol != null) protections.remove(symbol); }

    private void removeProtectionIfEmpty(String symbol, ProtectionInfo protection) {
        if (Double.compare(protection.getTakeProfitPrice(), 0D) == 0
                && Double.compare(protection.getStopLossPrice(), 0D) == 0) {
            protections.remove(symbol, protection);
        }
    }

    /**
     * 根据成交事件增量修正持仓数量。
     *
     * <p>主要用于 reduceOnly 平仓成交。在 User Data Stream 的 ACCOUNT_UPDATE 尚未到达，
     * 或某次事件没有及时刷新 TradingState 时，ORDER_TRADE_UPDATE 仍然可以保证本地状态及时归零。</p>
     */
    public synchronized PositionInfo applyTradeToPosition(String symbol, String side,
                                                            boolean reduceOnly, double lastFilledQty) {
        if (!reduceOnly || symbol == null || symbol.isBlank() || lastFilledQty <= 0D) return null;

        PositionInfo position = positions.get(symbol);
        if (position == null || Double.compare(position.getPositionAmt(), 0D) == 0) return position;

        double amount = position.getPositionAmt();
        if ("BUY".equalsIgnoreCase(side)) {
            amount += lastFilledQty;
        } else if ("SELL".equalsIgnoreCase(side)) {
            amount -= lastFilledQty;
        } else {
            return position;
        }

        if (Math.abs(amount) < 0.00000001D) amount = 0D;
        position.setPositionAmt(amount);
        positions.put(symbol, position);
        return position;
    }

    public void replaceOrders(List<OrderInfo> orderInfos) {
        orders.clear();
        if (orderInfos != null) orderInfos.forEach(this::updateOrder);
    }

    public void updateOrder(OrderInfo orderInfo) { if (orderInfo != null) orders.put(orderInfo.getOrderId(), orderInfo); }
    public void removeOrder(long orderId) { orders.remove(orderId); }

    public int clearSymbolOrders(String symbol) {
        if (symbol == null || symbol.isBlank()) return 0;
        int removed = 0;
        for (Map.Entry<Long, OrderInfo> entry : orders.entrySet()) {
            OrderInfo order = entry.getValue();
            if (order != null && symbol.equals(order.getSymbol()) && orders.remove(entry.getKey(), order)) removed++;
        }
        return removed;
    }

    public PositionInfo getPosition(String symbol) { return positions.get(symbol); }

    public List<OrderInfo> getOpenOrders(String symbol) {
        return orders.values().stream().filter(order -> order != null && symbol.equals(order.getSymbol())).toList();
    }

    public boolean isOrderSubmitting(String symbol) { return Boolean.TRUE.equals(orderSubmitting.get(symbol)); }
    public boolean startOrderSubmitting(String symbol) { return orderSubmitting.putIfAbsent(symbol, true) == null; }
    public void finishOrderSubmitting(String symbol) { orderSubmitting.remove(symbol); }

    public TradingExecutionState getExecutionState(String symbol) { return executionStates.getOrDefault(symbol, TradingExecutionState.READY); }

    public void updateExecutionState(String symbol, TradingExecutionState state) {
        if (symbol != null && state != null) executionStates.put(symbol, state);
    }

    /** 累加当天已实现盈亏。 */
    public synchronized void addTodayRealizedPnl(double realizedPnl) {
        LocalDate today = LocalDate.now(ACCOUNT_ZONE);
        if (!today.equals(realizedPnlDate)) {
            realizedPnlDate = today;
            todayRealizedPnl = 0D;
        }
        todayRealizedPnl += realizedPnl;
    }

    public void refreshExecutionState(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        PositionInfo position = positions.get(symbol);
        if (position != null && Double.compare(position.getPositionAmt(), 0D) != 0) {
            executionStates.put(symbol, TradingExecutionState.POSITION_OPEN);
            return;
        }
        if (!getOpenOrders(symbol).isEmpty()) {
            executionStates.put(symbol, TradingExecutionState.ORDER_OPEN);
            return;
        }
        if (!isOrderSubmitting(symbol)) executionStates.put(symbol, TradingExecutionState.READY);
    }
}
