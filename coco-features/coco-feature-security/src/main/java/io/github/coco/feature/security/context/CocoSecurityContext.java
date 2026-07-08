package io.github.coco.feature.security.context;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Coco 安全上下文。
 * <p>
 * 保存当前调用方的安全主体和认证状态，供鉴权、审计、租户和数据权限模块读取。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-security}</li>
 * </ul>
 * @param principal 安全主体
 * @param authenticated 是否已认证
 * @author patton174
 * @since 1.0.0
 */
public record CocoSecurityContext(CocoSecurityPrincipal principal, boolean authenticated) {

    /**
     * <p>
     * 创建安全上下文。
     * </p>
     */
    public CocoSecurityContext {
        principal = Objects.requireNonNull(principal, "principal must not be null");
    }

    /**
     * <p>
     * 创建已认证安全上下文。
     * </p>
     * @param principal 安全主体
     * @return 已认证安全上下文
     */
    public static CocoSecurityContext authenticated(CocoSecurityPrincipal principal) {
        return new CocoSecurityContext(principal, true);
    }

    /**
     * <p>
     * 创建匿名安全上下文。
     * </p>
     * @return 匿名安全上下文
     */
    public static CocoSecurityContext anonymous() {
        return new CocoSecurityContext(new CocoSecurityPrincipal("anonymous", "anonymous",
                Set.of(), Set.of(), Map.of()), false);
    }
}
