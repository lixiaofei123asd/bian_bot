package com.lxf.bian_bot;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.model.SymbolRule;
import com.lxf.bian_bot.service.BinanceAccountService;
import com.lxf.bian_bot.service.BinanceTradeService;
import com.lxf.bian_bot.model.AccountInfo;
import com.lxf.bian_bot.model.LeverageInfo;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.TimeInForce;
import com.lxf.bian_bot.model.TradingMarginType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class BinanceTradeServiceTest {

    @Autowired
    private BinanceTradeService tradeService;

    @Autowired
    private SymbolRuleCache symbolRuleCache;

    @Autowired
    private BinanceAccountService accountService;

    public @Test void tests() {
        SymbolRule btcusdt = symbolRuleCache.get("BTCUSDT");
        System.out.println(btcusdt);
    }

    public @Test void testAccount() {
        AccountInfo accountInfo = accountService.getAccountInfo();
        System.out.println(accountInfo);
    }

    // 平仓
    public @Test void testOrder3() {

        String symbol = "LABUSDT";
        tradeService.closeMarketOrder(symbol);

    }

    public @Test void testOrder2() {
        String symbol = "LABUSDT";
        Long leverage = 20L;
        // 设置20倍杠杆
        tradeService.setLeverage(symbol, leverage);

        //查看当前是逐仓还是全仓
        String marginType = tradeService.getMarginType(symbol);
        System.out.println("当前状态:" + marginType);
        if ("cross".equals(marginType)) {
            // 如果是全仓就改成逐仓
            boolean b = tradeService.setMarginType(symbol, TradingMarginType.ISOLATED);
            System.out.println(b ? "修改成功" : "修改失败");
        }

        // 买10份跌
        OrderInfo marketOrder = tradeService.createLimitOrder(symbol, Side.SELL, TimeInForce.GTC,10, 0.08150);
        System.out.println(marketOrder);
    }

    /**
     * 市价单买跌
     */
    public @Test void testOrder1() {
        String symbol = "LABUSDT";
        Long leverage = 20L;
        // 设置20倍杠杆
        tradeService.setLeverage(symbol, leverage);

        //查看当前是逐仓还是全仓
        String marginType = tradeService.getMarginType(symbol);
        System.out.println("当前状态:" + marginType);
        if ("cross".equals(marginType)) {
            // 如果是全仓就改成逐仓
            boolean b = tradeService.setMarginType(symbol, TradingMarginType.ISOLATED);
            System.out.println(b ? "修改成功" : "修改失败");
        }

        // 买10份跌
        OrderInfo marketOrder = tradeService.createMarketOrder(symbol, Side.SELL, 10);
        System.out.println(marketOrder);
    }


    /**
     * 设置杠杆测试
     */
    public @Test void testSetLeverage() {
        String symbol = "BTCUSDT";
        Long leverage = 50L;
        LeverageInfo leverageInfo = tradeService.setLeverage(symbol, leverage);
        System.out.println(leverageInfo);
    }

    /**
     * 修改逐仓或者全仓
     */
    public @Test void testSetMarginType() {

        //系统返回的是这两个字符串
        // cross
        // isolated
        String symbol = "BTCUSDT";
        String marginType = tradeService.getMarginType(symbol);
        System.out.println("当前状态:" + marginType);
        if(!"ISOLATED".equalsIgnoreCase(marginType)) {
            System.out.println("开始改成ISOLATED");
            boolean b = tradeService.setMarginType(symbol, TradingMarginType.ISOLATED);
        } else {
            System.out.println("开始改成CROSSED");
            boolean b = tradeService.setMarginType(symbol, TradingMarginType.CROSSED);
        }

        System.out.println("查看修改结果:" + tradeService.getMarginType(symbol));

    }

}
