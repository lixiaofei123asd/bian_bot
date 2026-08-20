package com.lxf.bian_bot.analysis;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StrategyRegistry implements InitializingBean {

    private static final String STRATEGY_PACKAGE = "com.lxf.bian_bot.analysis.strategy";

    private Map<String, Strategy> strategies = Map.of();

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

    public Strategy getStrategy(String strategyName) {
        if (strategyName == null) {
            return null;
        }
        return strategies.get(strategyName.trim().toUpperCase(Locale.ROOT));
    }

    public boolean contains(String strategyName) {
        return getStrategy(strategyName) != null;
    }

    // 根据类名实例化Strategy实现类
    private Strategy instantiateStrategy(String className) {
        try {
            return BeanUtils.instantiateClass(Class.forName(className).asSubclass(Strategy.class));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("无法加载Strategy: " + className, e);
        }
    }
}
