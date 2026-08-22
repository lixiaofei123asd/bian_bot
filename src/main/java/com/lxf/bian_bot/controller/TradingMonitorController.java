package com.lxf.bian_bot.controller;

import com.lxf.bian_bot.trading.state.TradingState;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供交易监控页面所需的实时内存状态。 */
@RestController
@RequestMapping("/api/monitor")
public class TradingMonitorController {

    private final TradingState tradingState;

    public TradingMonitorController(TradingState tradingState) {
        this.tradingState = tradingState;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", tradingState.getAccountInfo());
        result.put("todayRealizedPnl", tradingState.getTodayRealizedPnl());
        result.put("positions", tradingState.getPositions().values());
        result.put("orders", tradingState.getOrders().values());
        result.put("executionStates", tradingState.getExecutionStates());
        result.put("updatedAt", Instant.now().toEpochMilli());
        return result;
    }
}
