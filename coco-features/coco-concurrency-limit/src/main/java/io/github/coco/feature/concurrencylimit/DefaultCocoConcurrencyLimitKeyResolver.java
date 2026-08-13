package io.github.coco.feature.concurrencylimit;

import java.util.Objects;

import io.github.coco.feature.web.context.CocoWebRequestSnapshot;

/**
 * 使用 Coco 可信客户端 IP 结果的默认并发限制键解析器。
 */
public final class DefaultCocoConcurrencyLimitKeyResolver implements CocoConcurrencyLimitKeyResolver {

    private static final String ANONYMOUS_KEY = "anonymous";

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolve(CocoWebRequestSnapshot snapshot, CocoConcurrencyLimitRoute route) {
        CocoWebRequestSnapshot checkedSnapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(route, "route must not be null");
        String clientIp = checkedSnapshot.clientIp();
        return clientIp == null || clientIp.isBlank() ? ANONYMOUS_KEY : clientIp.trim();
    }
}
