package io.github.coco.feature.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 限流键解析 SPI。
 * <p>
 * 业务项目可替换该 Bean，将经过自身认证或网关验证的应用、设备或客户标识映射为限流主体。实现方必须只使用
 * 已验证的身份信息，不能直接信任客户端可伪造的请求头。
 * </p>
 */
@FunctionalInterface
public interface CocoRateLimitKeyResolver {

    /**
     * 解析当前请求的限流键。
     * @param request 当前 Coco Web 请求快照
     * @param route 命中的限流路由
     * @return 原子限流存储使用的键
     */
    CocoRateLimitKey resolve(HttpServletRequest request, CocoRateLimitRoute route);
}
