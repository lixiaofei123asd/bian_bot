package com.lxf.bian_bot.service.impl;

import com.binance.connector.client.common.ApiResponse;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.api.UserDataStreamsApi;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.StartUserDataStreamResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.lxf.bian_bot.config.model.BinanceConfig;
import com.lxf.bian_bot.model.AccountInfo;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.trading.state.TradingExecutionState;
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

/** Binance Futures User Data Stream。 */
@Slf4j
@Service
public class BinanceUserDataStreamServiceImpl implements com.lxf.bian_bot.service.BinanceUserDataStreamService {

    private static final long KEEPALIVE_INTERVAL_MILLIS = 30 * 60 * 1000L;

    private final UserDataStreamsApi userDataStreamsApi;
    private final TradingState tradingState;
    private final BinanceUserDataStreamClient client;

    private final List<Consumer<AccountInfo>> accountListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PositionInfo>> positionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<OrderInfo>> orderListeners = new CopyOnWriteArrayList<>();

    private volatile String listenKey;
    private volatile long lastKeepaliveTime;
    private volatile boolean started;

    public BinanceUserDataStreamServiceImpl(UserDataStreamsApi userDataStreamsApi,
                                            BinanceConfig binanceConfig,
                                            TradingState tradingState) {
        this.userDataStreamsApi = userDataStreamsApi;
        this.tradingState = tradingState;
        this.client = new BinanceUserDataStreamClient(
                binanceConfig.getWebSocketStreamsUrl(),
                binanceConfig.isProxyEnabled(),
                binanceConfig.getProxyHost(),
                binanceConfig.getProxyPort());
        this.client.setMessageListener(this::handleMessage);
        this.client.setErrorListener(error -> log.error("User Data Stream WebSocket 异常", error));
        this.client.setCloseListener(() -> {
            if (started) log.warn("User Data Stream WebSocket 已关闭，等待自动重连");
        });
    }

    @PostConstruct
    @Override
    public void start() {
        if (started) return;
        started = true;
        startStream();
    }

    private synchronized void startStream() {
        if (!started) return;
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

    @Scheduled(fixedDelay = 60_000L)
    public void maintainStream() {
        if (!started) return;
        if (listenKey == null || listenKey.isBlank()) {
            startStream();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastKeepaliveTime >= KEEPALIVE_INTERVAL_MILLIS) {
            try {
                userDataStreamsApi.keepaliveUserDataStream();
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
            case "ACCOUNT_UPDATE" -> handleAccountUpdate(root);
            case "ORDER_TRADE_UPDATE" -> handleOrderTradeUpdate(root);
            case "listenKeyExpired" -> {
                log.warn("Binance User Data Stream listenKey 已过期，重新获取");
                listenKey = null;
                client.close();
                startStream();
            }
            default -> log.debug("Binance User Data Stream 收到事件: {}", eventType);
        }
    }

    private void handleAccountUpdate(JsonNode root) {
        JsonNode accountNode = root.path("a");
        log.info("========== Binance User Data ACCOUNT_UPDATE ==========");
        log.info("事件时间: {}", root.path("E").asLong());
        log.info("账户更新原因: {}", accountNode.path("m").asText());

        JsonNode balances = accountNode.path("B");
        if (balances.isArray()) {
            for (JsonNode balance : balances) {
                if ("USDT".equals(balance.path("a").asText())) {
                    log.info("USDT余额更新: walletBalance={}, crossWalletBalance={}, balanceChange={}",
                            balance.path("wb").asText(), balance.path("cw").asText(), balance.path("bc").asText());
                }
            }
        }

        JsonNode positions = accountNode.path("P");
        if (positions.isArray()) {
            for (JsonNode position : positions) {
                log.info("持仓更新: symbol={}, positionAmt={}, entryPrice={}, breakEvenPrice={}, unrealizedProfit={}, positionSide={}",
                        position.path("s").asText(), position.path("pa").asText(), position.path("ep").asText(),
                        position.path("bep").asText(), position.path("up").asText(), position.path("ps").asText());
            }
        }
        log.info("=====================================================");

        updateAccount(balances, positions);
        updatePositions(positions);
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
            for (JsonNode position : positions) unrealizedProfit += number(position, "up", 0D);
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
        if (positions == null || !positions.isArray()) return;

        for (JsonNode node : positions) {
            String symbol = text(node, "s");
            if (symbol == null || symbol.isBlank()) continue;

            PositionInfo oldPosition = tradingState.getPosition(symbol);
            double oldAmount = oldPosition == null ? 0D : oldPosition.getPositionAmt();

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

            double newAmount = position.getPositionAmt();
            tradingState.updatePosition(position);

            if (Double.compare(oldAmount, 0D) != 0 && Double.compare(newAmount, 0D) == 0) {
                int removed = tradingState.clearSymbolOrders(symbol);
                tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
                log.info("{} 持仓已归零，清理 TradingState 残留订单: removed={}, executionState=READY",
                        symbol, removed);
            } else {
                tradingState.refreshExecutionState(symbol);
            }

            notifyListeners(positionListeners, position);
        }
    }

    private void handleOrderTradeUpdate(JsonNode root) {
        JsonNode orderNode = root.path("o");
        if (orderNode.isMissingNode()) return;

        String symbol = text(orderNode, "s");
        long orderId = orderNode.path("i").asLong(0L);
        if (symbol == null || orderId == 0L) return;

        String status = text(orderNode, "X");
        String executionType = text(orderNode, "x");
        boolean reduceOnly = orderNode.path("R").asBoolean(false);
        double lastFilledQty = number(orderNode, "l", 0D);

        if ("TRADE".equals(executionType)) {
            tradingState.addTodayRealizedPnl(number(orderNode, "rp", 0D));

            // reduceOnly 成交时，先用 ORDER_TRADE_UPDATE 增量修正本地持仓。
            // 这样即使 ACCOUNT_UPDATE 稍后到达，TradingState 也不会在这段时间继续保留旧仓位。
            PositionInfo adjustedPosition = tradingState.applyTradeToPosition(
                    symbol, text(orderNode, "S"), reduceOnly, lastFilledQty);
            if (adjustedPosition != null) {
                log.info("根据成交事件同步本地持仓: symbol={}, side={}, reduceOnly={}, lastFilledQty={}, positionAmt={}",
                        symbol, text(orderNode, "S"), reduceOnly, lastFilledQty, adjustedPosition.getPositionAmt());

                if (!adjustedPosition.hasPosition()) {
                    int removed = tradingState.clearSymbolOrders(symbol);
                    tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
                    log.info("{} reduceOnly 成交后持仓已归零，清理 TradingState 残留订单: removed={}, executionState=READY",
                            symbol, removed);
                } else {
                    tradingState.refreshExecutionState(symbol);
                }
            }
        }

        log.info("========== Binance User Data ORDER_TRADE_UPDATE ==========");
        log.info("订单更新: symbol={}, orderId={}, clientOrderId={}, side={}, type={}, status={}, execType={}, orderQty={}, orderPrice={}, avgPrice={}, lastFilledQty={}, lastFilledPrice={}",
                symbol, orderId, text(orderNode, "c"), text(orderNode, "S"), text(orderNode, "o"), status,
                executionType, text(orderNode, "q"), text(orderNode, "p"), text(orderNode, "ap"),
                text(orderNode, "l"), text(orderNode, "L"));
        log.info("========================================================");

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
                .reduceOnly(reduceOnly)
                .positionSide(text(orderNode, "ps"))
                .updateTime(orderNode.path("T").asLong(System.currentTimeMillis()))
                .build();

        if (isOpenStatus(status)) tradingState.updateOrder(order);
        else tradingState.removeOrder(orderId);

        tradingState.refreshExecutionState(symbol);
        notifyListeners(orderListeners, order);
    }

    private boolean isOpenStatus(String status) { return "NEW".equals(status) || "PARTIALLY_FILLED".equals(status); }

    private double number(JsonNode node, String field, double defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return defaultValue;
        try { return Double.parseDouble(value.asText()); }
        catch (Exception e) { return defaultValue; }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private <T> void notifyListeners(List<Consumer<T>> listeners, T value) {
        for (Consumer<T> listener : listeners) {
            try { listener.accept(value); }
            catch (Exception e) { log.error("User Data Stream listener 执行异常", e); }
        }
    }

    @Override
    public void addOrderListener(Consumer<OrderInfo> listener) { if (listener != null) orderListeners.add(listener); }

    @Override
    public void addPositionListener(Consumer<PositionInfo> listener) { if (listener != null) positionListeners.add(listener); }

    public void addAccountListener(Consumer<AccountInfo> listener) { if (listener != null) accountListeners.add(listener); }

    @Override
    public boolean isConnected() { return client.isConnected(); }

    @PreDestroy
    @Override
    public synchronized void stop() {
        started = false;
        try {
            if (listenKey != null && !listenKey.isBlank()) userDataStreamsApi.closeUserDataStream();
        } catch (Exception e) {
            log.debug("关闭 Binance User Data Stream listenKey 异常", e);
        } finally {
            listenKey = null;
            client.close();
        }
    }
}
