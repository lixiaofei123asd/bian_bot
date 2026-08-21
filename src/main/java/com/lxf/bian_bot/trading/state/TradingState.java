package com.lxf.bian_bot.trading.state;

import com.lxf.bian_bot.model.AccountInfo;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 全局交易状态中心。
 *
 * <p>Spring 默认单例 Bean，整个应用内所有 Service 注入到的都是同一个 TradingState 实例。
 * REST 负责初始化，User Data Stream 和交易服务负责实时更新，业务层只从这里读取当前状态。
 */
@Getter
@Component
public class TradingState {

    private volatile AccountInfo accountInfo;
    private final Map<String, PositionInfo> positions = new ConcurrentHashMap<>();
    private final Map<Long, OrderInfo> orders = new ConcurrentHashMap<>();
    private final Map<String, Boolean> orderSubmitting = new ConcurrentHashMap<>();
    private final Map<String, TradingExecutionState> executionStates = new ConcurrentHashMap<>();

    public void updateAccount(AccountInfo accountInfo) {
        if (accountInfo != null) {
            this.accountInfo = accountInfo;
        }
    }

    public void updatePosition(PositionInfo positionInfo) {
        if (positionInfo == null || positionInfo.getSymbol() == null) {
            return;
        }
        positions.put(positionInfo.getSymbol(), positionInfo);
    }

    public void replaceOrders(List<OrderInfo> orderInfos) {
        orders.clear();
        if (orderInfos == null) {
            return;
        }
        for (OrderInfo orderInfo : orderInfos) {
            updateOrder(orderInfo);
        }
    }

    public void updateOrder(OrderInfo orderInfo) {
        if (orderInfo == null) {
            return;
        }
        orders.put(orderInfo.getOrderId(), orderInfo);
    }

    public void removeOrder(long orderId) {
        orders.remove(orderId);
    }

    /**
     * 清理指定交易对在内存中的活动订单状态。
     *
     * <p>主要用于 User Data Stream 确认持仓从非零变为零后的状态收敛，
     * 防止已经完成的开仓/保护生命周期残留在 TradingState 中阻塞下一次交易。
     */
    public int clearSymbolOrders(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<Long, OrderInfo> entry : orders.entrySet()) {
            OrderInfo order = entry.getValue();
            if (order != null && symbol.equals(order.getSymbol()) && orders.remove(entry.getKey(), order)) {
                removed++;
            }
        }
        return removed;
    }

    public PositionInfo getPosition(String symbol) {
        return positions.get(symbol);
    }

    public List<OrderInfo> getOpenOrders(String symbol) {
        return orders.values().stream()
                .filter(order -> order != null && symbol.equals(order.getSymbol()))
                .toList();
    }

    public boolean isOrderSubmitting(String symbol) {
        return Boolean.TRUE.equals(orderSubmitting.get(symbol));
    }

    public boolean startOrderSubmitting(String symbol) {
        return orderSubmitting.putIfAbsent(symbol, true) == null;
    }

    public void finishOrderSubmitting(String symbol) {
        orderSubmitting.remove(symbol);
    }

    public TradingExecutionState getExecutionState(String symbol) {
        return executionStates.getOrDefault(symbol, TradingExecutionState.READY);
    }

    public void updateExecutionState(String symbol, TradingExecutionState state) {
        if (symbol != null && state != null) {
            executionStates.put(symbol, state);
        }
    }

    /**
     * 根据当前持仓和活动订单重新计算指定交易对的执行状态。
     */
    public void refreshExecutionState(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }

        PositionInfo position = positions.get(symbol);
        if (position != null && Double.compare(position.getPositionAmt(), 0D) != 0) {
            executionStates.put(symbol, TradingExecutionState.POSITION_OPEN);
            return;
        }

        if (!getOpenOrders(symbol).isEmpty()) {
            executionStates.put(symbol, TradingExecutionState.ORDER_OPEN);
            return;
        }

        if (!isOrderSubmitting(symbol)) {
            executionStates.put(symbol, TradingExecutionState.READY);
        }
    }
}
