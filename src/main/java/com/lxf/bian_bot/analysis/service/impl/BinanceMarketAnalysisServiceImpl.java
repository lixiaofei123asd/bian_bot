package com.lxf.bian_bot.analysis.service.impl;

import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.service.BinanceMarketAnalysisService;
import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.config.StrategyConfig;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.TradingDecision;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceMarketAnalysisServiceImpl implements BinanceMarketAnalysisService, InitializingBean {

    private static final String KLINE_INTERVAL = "1m";
    private static final String STRATEGY_PACKAGE = "com.lxf.bian_bot.analysis.strategy";

    private final KlineCache klineCache;
    private final StrategyConfig strategyConfig;

    private Map<String, Strategy> strategies = Map.of();

    public BinanceMarketAnalysisServiceImpl(KlineCache klineCache, StrategyConfig strategyConfig) {
        this.klineCache = klineCache;
        this.strategyConfig = strategyConfig;
    }

    @Override
    public void afterPropertiesSet() {
        // 程序启动时扫描strategy包，自动实例化所有Strategy实现类
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Strategy.class));

        this.strategies = scanner.findCandidateComponents(STRATEGY_PACKAGE).stream()
                .map(beanDefinition -> instantiateStrategy(beanDefinition.getBeanClassName()))
                .collect(Collectors.toUnmodifiableMap(
                        strategy -> strategy.getClass().getSimpleName().toUpperCase(Locale.ROOT),
                        strategy -> strategy));

        log.info("Strategy初始化完成: {}", strategies.keySet());
    }

    // 根据类名实例化Strategy实现类
    private Strategy instantiateStrategy(String className) {
        try {
            return BeanUtils.instantiateClass(Class.forName(className).asSubclass(Strategy.class));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("无法加载Strategy: " + className, e);
        }
    }

    @Override
    public TradingDecision analyze(String symbol) {
        log.info("========== 开始市场分析 {} ==========", symbol);

        // 从本地K线缓存获取数据
        List<BinanceKline> klines = klineCache.get(symbol, KLINE_INTERVAL);
        log.debug("从 KlineCache 获取 K线: symbol={}, interval={}, size={}", symbol, KLINE_INTERVAL, klines.size());

        if (klines.isEmpty()) {
            log.warn("{} KlineCache 中没有 K线数据", symbol);
            return buildWaitDecision(symbol, "KlineCache 中没有 K线数据", null);
        }

        BigDecimal currentPrice = klines.getLast().getClosePrice();

        // 根据币种获取唯一策略
        String strategyName = strategyConfig.getStrategyName(symbol);
        if (strategyName == null || strategyName.isBlank()) {
            log.warn("{} 未配置交易策略", symbol);
            return buildWaitDecision(symbol, "未配置交易策略", currentPrice);
        }

        Strategy strategy = strategies.get(strategyName.trim().toUpperCase(Locale.ROOT));
        if (strategy == null) {
            log.warn("{} 配置了不存在的策略: {}", symbol, strategyName);
            return buildWaitDecision(symbol, "配置了不存在的策略: " + strategyName, currentPrice);
        }

        // 同一个币种只执行配置的一个策略
        TradingSignal signal = strategy.execute(klines);
        TradingDecision decision = buildDecision(symbol, currentPrice, signal, strategyName);

        log.info("市场分析完成: symbol={}, strategy={}, decision={}", symbol, strategyName, decision.getDecision());
        return decision;
    }

    // 将策略信号转换为最终交易决策
    private TradingDecision buildDecision(String symbol, BigDecimal currentPrice, TradingSignal signal, String strategyName) {
        TradingDecision.DecisionType decisionType = switch (signal) {
            case LONG -> TradingDecision.DecisionType.ENTER_LONG;
            case SHORT -> TradingDecision.DecisionType.ENTER_SHORT;
            case WAIT -> TradingDecision.DecisionType.WAIT;
        };

        TradingDecision.Direction direction = switch (signal) {
            case LONG -> TradingDecision.Direction.LONG;
            case SHORT -> TradingDecision.Direction.SHORT;
            case WAIT -> null;
        };

        return TradingDecision.builder()
                .symbol(symbol)
                .decision(decisionType)
                .direction(direction)
                .currentPrice(currentPrice)
                .reason("策略: " + strategyName + ", 信号: " + signal)
                .build();
    }

    // 创建WAIT决策
    private TradingDecision buildWaitDecision(String symbol, String reason, BigDecimal currentPrice) {
        return TradingDecision.builder()
                .symbol(symbol)
                .decision(TradingDecision.DecisionType.WAIT)
                .currentPrice(currentPrice)
                .reason(reason)
                .build();
    }
}
