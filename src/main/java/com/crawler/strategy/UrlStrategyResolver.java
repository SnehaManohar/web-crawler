package com.crawler.strategy;

import com.crawler.model.UrlType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Runtime {@link UrlType} -&gt; {@link UrlStrategy} lookup, built once from every
 * {@code UrlStrategy} bean on the classpath. Adding a strategy needs no change here - Spring
 * injects the new bean into the list and it registers itself by {@link UrlStrategy#handles()}.
 */
@Component
public class UrlStrategyResolver {

    private final Map<UrlType, UrlStrategy> strategies = new EnumMap<>(UrlType.class);

    public UrlStrategyResolver(List<UrlStrategy> allStrategies) {
        for (UrlStrategy strategy : allStrategies) {
            UrlStrategy existing = strategies.putIfAbsent(strategy.handles(), strategy);
            if (existing != null) {
                throw new IllegalStateException(
                        "Two strategies both claim " + strategy.handles() + ": "
                                + existing.getClass().getSimpleName() + " and "
                                + strategy.getClass().getSimpleName());
            }
        }
        for (UrlType type : UrlType.values()) {
            if (!strategies.containsKey(type)) {
                throw new IllegalStateException("No UrlStrategy registered for " + type);
            }
        }
    }

    public UrlStrategy resolve(UrlType type) {
        return strategies.get(type);
    }
}
