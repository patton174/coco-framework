package io.github.coco.feature.security.authorization;

import io.github.coco.feature.security.context.CocoSecurityContextResolver;

/**
 * 方法级授权决策 SPI。
 * <p>
 * 业务应用可声明同类型 Bean 覆盖默认实现，以在不改变 {@link CocoAuthorize} 契约的前提下替换授权决策。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoMethodAuthorizationManager {

    /**
     * 校验当前调用是否满足授权要求。
     * @param requirement 已规范化授权要求
     * @param contextResolver 当前安全上下文解析器
     */
    void authorize(CocoAuthorizationRequirement requirement, CocoSecurityContextResolver contextResolver);
}
