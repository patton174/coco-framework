package io.github.coco.feature.concurrencylimit;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 复用 Coco Web 请求匹配器的有序并发限制路由匹配器。
 */
public final class DefaultCocoConcurrencyLimitRouteMatcher implements CocoConcurrencyLimitRouteMatcher {

    private final List<CocoConcurrencyLimitRoute> routes;

    private final CocoWebRequestMatcher requestMatcher;

    /**
     * 创建有序并发限制路由匹配器。
     * @param properties 并发限制配置
     * @param requestMatcher Coco Web 请求匹配器
     */
    public DefaultCocoConcurrencyLimitRouteMatcher(CocoConcurrencyLimitProperties properties,
            CocoWebRequestMatcher requestMatcher) {
        CocoConcurrencyLimitProperties checkedProperties = properties == null
                ? new CocoConcurrencyLimitProperties() : properties;
        this.requestMatcher = Objects.requireNonNull(requestMatcher, "requestMatcher must not be null");
        validateProperties(checkedProperties);
        this.routes = checkedProperties.getRoutes().stream()
                .sorted(Comparator.comparingInt(CocoConcurrencyLimitRoute::getOrder))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoConcurrencyLimitRoute> resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return this.routes.stream()
                .filter(route -> !route.getMatcher().isEmpty())
                .filter(route -> this.requestMatcher.matches(request, List.of(route.getMatcher())))
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoConcurrencyLimitRoute> resolve(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return Optional.empty();
        }
        String checkedRouteId = routeId.trim();
        return this.routes.stream().filter(route -> checkedRouteId.equals(route.getId())).findFirst();
    }

    private static void validateProperties(CocoConcurrencyLimitProperties properties) {
        if (properties.getGlobalLimit() < 0) {
            throw new IllegalStateException("coco.concurrency-limit.global-limit must not be negative");
        }
        Set<String> routeIds = new HashSet<>();
        for (CocoConcurrencyLimitRoute route : properties.getRoutes()) {
            if (route == null || !route.valid(properties.getGlobalLimit())) {
                throw new IllegalStateException("Each coco.concurrency-limit.routes entry needs a unique id and at least one positive limit");
            }
            if (!routeIds.add(route.getId())) {
                throw new IllegalStateException("Duplicate coco.concurrency-limit route id: " + route.getId());
            }
        }
        CocoConcurrencyLimitProperties.InMemory inMemory = properties.getInMemory();
        if (inMemory.getMaxEntries() <= 0) {
            throw new IllegalStateException("coco.concurrency-limit.in-memory.max-entries must be positive");
        }
        CocoConcurrencyLimitProperties.Response response = properties.getResponse();
        if (response.getStatus() < 400 || response.getStatus() > 599) {
            throw new IllegalStateException("coco.concurrency-limit.response.status must be between 400 and 599");
        }
        if (response.getRetryAfterSeconds() <= 0) {
            throw new IllegalStateException("coco.concurrency-limit.response.retry-after-seconds must be positive");
        }
    }
}
