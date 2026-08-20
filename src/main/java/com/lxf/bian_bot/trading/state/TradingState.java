package com.lxf.bian_bot.trading.state;

import com.lxf.bian_bot.model.AccountInfo;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class TradingState {

    private volatile AccountInfo accountInfo;
    private final Map<String, PositionInfo> positions = new ConcurrentHashMap<>();
    private final Map<Long, OrderInfo> orders = new ConcurrentHashMap<>();
    private final Map<String, Boolean> orderSubmitting = new ConcurrentHashMap<>();
    private final Map<String, TradingExecutionState> executionStates = new ConcurrentHashMap<>();

    public void updateAccount(AccountInfo accountInfo) {
        this.accountInfo = accountInfo;
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

    public PositionInfo getPosition(String symbol) {
        return positions.get(symbol);
    }

    public List<OrderInfo> getOpenOrders(String symbol) {
        return orders.values().stream()
                .filter(order -> symbol.equals(order.getSymbol()))
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
}
