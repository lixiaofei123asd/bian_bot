package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.UserDataStreamsApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.StartUserDataStreamResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.config.BinanceWebSocketProperties;
import com.lxf.bian_bot.model.AccountInfo;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.trading.state.TradingState;
import com.lxf.bian_bot.websocket.BinanceUserDataStreamClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Binance Futures User Data Stream。
 *
 * <p>负责实时接收账户、持仓和订单成交事件，并将最新状态写入 TradingState。
 * REST 定时刷新仍然保留，作为断线后的状态校准和兜底机制。
 */
@Slf4j
@Service
public class BinanceUserDataStreamServiceImpl implements com.lxf.bian_bot.service.BinanceUserDataStreamService {

    private static final long KEEPALIVE_INTERVAL_MILLIS = 30 * 60 * 1000L;

    private final UserDataStreamsApi userDataStreamsApi;
    private final BinanceProperties binanceProperties;
    private final BinanceWebSocketProperties webSocketProperties;
    private final TradingState tradingState;
    private final BinanceUserDataStreamClient client;

    private final List<Consumer<AccountInfo>> accountListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PositionInfo>> positionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<OrderInfo>> orderListeners = new CopyOnWriteArrayList<>();

    private volatile String listenKey;
    private volatile long lastKeepaliveTime;
    private volatile boolean started;

    public BinanceUserDataStreamServiceImpl(UserDataStreamsApi userDataStreamsApi,
                                            BinanceProperties binanceProperties,
                                            BinanceWebSocketProperties webSocketProperties,
                                            TradingState tradingState) {
        this.userDataStreamsApi = userDataStreamsApi;
        this.binanceProperties = binanceProperties;
        this.webSocketProperties = webSocketProperties;
        this.tradingState = tradingState;
        this.client = new BinanceUserDataStreamClient(
                webSocketProperties.getWebSocketStreamsUrl(),
                webSocketProperties.isProxyEnabled(),
                webSocketProperties.getProxyHost(),
                webSocketProperties.getProxyPort()
        );
        this.client.setMessageListener(this::handleMessage);
        this.client.setErrorListener(error -> log.error("User Data Stream WebSocket 异常", error));
        this.client.setCloseListener(() -> {
            if (started) {
                log.warn("User Data Stream WebSocket 已关闭，等待自动重连");
            }
        });
    }

    @PostConstruct
    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        startStream();
    }

    private synchronized void startStream() {
        if (!started) {
            return;
        }
        try {
            ApiResponse<StartUserDataStreamResponse> response = userDataStreamsApi.startUserDataStream();
            StartUserDataStreamResponse data = response.getData();
            if (data == null || data.getListenKey() == null || data.getListenKey().isBlank()) {
                throw new IllegalStateException("Binance 未返回有效 listenKey");
            }

            listenKey = data.getListenKey();
            lastKeepaliveTime = System.currentTimeMillis();
            client.connect(listenKey);
            log.info("Binance Futures User Data Stream 启动成功");
        } catch (Exception e) {
            log.error("启动 Binance Futures User Data Stream 失败", e);
        }
    }

    /**
     * listenKey 官方有效期为60分钟，这里每30分钟续期一次。
     */
    @Scheduled(fixedDelay = 60_000L)
    public void maintainStream() {
        if (!started) {
            return;
        }

        if (listenKey == null || listenKey.isBlank()) {
            startStream();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastKeepaliveTime >= KEEPALIVE_INTERVAL_MILLIS) {
            try {
                userDataStreamsApi.keepaliveUserDataStream(listenKey);
                lastKeepaliveTime = now;
                log.debug("Binance User Data Stream listenKey 续期成功");
            } catch (Exception e) {
                log.error("Binance User Data Stream listenKey 续期失败，将重新建立连接", e);
                client.close();
                listenKey = null;
            }
        }

        if (!client.isConnected() && listenKey != null) {
            log.warn("Binance User Data Stream 当前未连接，重新建立 WebSocket");
            client.connect(listenKey);
        }
    }

    private void handleMessage(JsonNode root) {
        String eventType = root.path("e").asText();
        switch (eventType) {
            case "ACCOUNT_UPDATE" -> handleAccountUpdate(root.path("a"));
            case "ORDER_TRADE_UPDATE" -> handleOrderTradeUpdate(root.path("o"));
            case "listenKeyExpired" -> {
                log.warn("Binance User Data Stream listenKey 已过期，重新获取");
                listenKey = null;
                client.close();
                startStream();
            }
            default -> log.debug("Binance User Data Stream 收到事件: {}", eventType);
        }
    }

    private void handleAccountUpdate(JsonNode accountNode) {
        updateAccount(accountNode.path("B"), accountNode.path("P"));
        updatePositions(accountNode.path("P"));
    }

    private void updateAccount(JsonNode balances, JsonNode positions) {
        AccountInfo old = tradingState.getAccountInfo();
        double walletBalance = old == null ? 0D : old.getTotalWalletBalance();
        double marginBalance = old == null ? 0D : old.getTotalMarginBalance();
        double availableBalance = old == null ? 0D : old.getAvailableBalance();
        double maxWithdraw = old == null ? 0D : old.getMaxWithdrawAmount();
        boolean canTrade = old == null || old.isCanTrade();
        double unrealizedProfit = 0D;

        if (balances != null && balances.isArray()) {
            for (JsonNode balance : balances) {
                if ("USDT".equals(balance.path("a").asText())) {
                    walletBalance = number(balance, "wb", walletBalance);
                    marginBalance = walletBalance;
                    break;
                }
            }
        }

        if (positions != null && positions.isArray()) {
            for (JsonNode position : positions) {
                unrealizedProfit += number(position, "up", 0D);
            }
        }

        AccountInfo accountInfo = AccountInfo.builder()
                .totalWalletBalance(walletBalance)
                .totalMarginBalance(marginBalance + unrealizedProfit)
                .totalUnrealizedProfit(unrealizedProfit)
                .availableBalance(availableBalance)
                .maxWithdrawAmount(maxWithdraw)
                .canTrade(canTrade)
                .build();

        tradingState.updateAccount(accountInfo);
        notifyListeners(accountListeners, accountInfo);
    }

    private void updatePositions(JsonNode positions) {
        if (positions == null || !positions.isArray()) {
            return;
        }

        for (JsonNode node : positions) {
            String symbol = text(node, "s");
            if (symbol == null || symbol.isBlank()) {
                continue;
            }

            PositionInfo position = PositionInfo.builder()
                    .symbol(symbol)
                    .positionAmt(number(node, "pa", 0D))
                    .entryPrice(number(node, "ep", 0D))
                    .breakEvenPrice(number(node, "bep", 0D))
                    .unrealizedProfit(number(node, "up", 0D))
                    .positionSide(text(node, "ps"))
                    .marginType(text(node, "mt"))
                    .autoAddMargin("true".equalsIgnoreCase(text(node, "iw")))
                    .build();

            tradingState.updatePosition(position);
            notifyListeners(positionListeners, position);
        }
    }

    private void handleOrderTradeUpdate(JsonNode orderNode) {
        if (orderNode == null || orderNode.isMissingNode()) {
            return;
        }

        String symbol = text(orderNode, "s");
        long orderId = orderNode.path("i").asLong(0L);
        if (symbol == null || orderId == 0L) {
            return;
        }

        String status = text(orderNode, "X");
        OrderInfo order = OrderInfo.builder()
                .symbol(symbol)
                .orderId(orderId)
                .clientOrderId(text(orderNode, "c"))
                .side(text(orderNode, "S"))
                .type(text(orderNode, "o"))
                .origQty(number(orderNode, "q", 0D))
                .executedQty(number(orderNode, "z", 0D))
                .avgPrice(number(orderNode, "ap", 0D))
                .price(number(orderNode, "p", 0D))
                .status(status)
                .reduceOnly(orderNode.path("R").asBoolean(false))
                .positionSide(text(orderNode, "ps"))
                .updateTime(orderNode.path("T").asLong(System.currentTimeMillis()))
                .build();

        if (isOpenStatus(status)) {
            tradingState.updateOrder(order);
        } else {
            tradingState.removeOrder(orderId);
        }

        log.info("User Data Stream 订单更新: symbol={}, orderId={}, status={}, executedQty={}, avgPrice={}",
                symbol, orderId, status, order.getExecutedQty(), order.getAvgPrice());
        notifyListeners(orderListeners, order);
    }

    private boolean isOpenStatus(String status) {
        return "NEW".equals(status) || "PARTIALLY_FILLED".equals(status);
    }

    private double number(JsonNode node, String field, double defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.asText());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private <T> void notifyListeners(List<Consumer<T>> listeners, T value) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(value);
            } catch (Exception e) {
                log.error("User Data Stream listener 执行异常", e);
            }
        }
    }

    @Override
    public void addOrderListener(Consumer<OrderInfo> listener) {
        if (listener != null) {
            orderListeners.add(listener);
        }
    }

    @Override
    public void addPositionListener(Consumer<PositionInfo> listener) {
        if (listener != null) {
            positionListeners.add(listener);
        }
    }

    public void addAccountListener(Consumer<AccountInfo> listener) {
        if (listener != null) {
            accountListeners.add(listener);
        }
    }

    @Override
    public boolean isConnected() {
        return client.isConnected();
    }

    @PreDestroy
    @Override
    public synchronized void stop() {
        started = false;
        try {
            if (listenKey != null && !listenKey.isBlank()) {
                userDataStreamsApi.closeUserDataStream(listenKey);
            }
        } catch (Exception e) {
            log.debug("关闭 Binance User Data Stream listenKey 异常", e);
        } finally {
            listenKey = null;
            client.close();
        }
    }
}
