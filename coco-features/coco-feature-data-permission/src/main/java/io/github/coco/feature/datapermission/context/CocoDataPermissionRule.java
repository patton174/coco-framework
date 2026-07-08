package io.github.coco.feature.datapermission.context;

import java.util.Objects;
import java.util.Set;

/**
 * Coco 数据权限规则。
 * <p>
 * 描述某个资源的数据范围和范围值，后续 SQL 拦截器、查询构造器或业务适配器都可基于该规则生成最终条件。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @param resource 资源标识
 * @param scope 数据范围
 * @param values 范围值集合
 * @author patton174
 * @since 1.0.0
 */
public record CocoDataPermissionRule(String resource, CocoDataScope scope, Set<String> values) {

    /**
     * <p>
     * 创建数据权限规则并复制范围值集合。
     * </p>
     */
    public CocoDataPermissionRule {
        resource = requireText(resource, "resource");
        scope = Objects.requireNonNull(scope, "scope must not be null");
        values = Set.copyOf(values == null ? Set.of() : values);
    }

    /**
     * <p>
     * 创建全部数据范围规则。
     * </p>
     * @param resource 资源标识
     * @return 全部数据范围规则
     */
    public static CocoDataPermissionRule all(String resource) {
        return new CocoDataPermissionRule(resource, CocoDataScope.ALL, Set.of());
    }

    /**
     * <p>
     * 创建拒绝访问规则。
     * </p>
     * @param resource 资源标识
     * @return 拒绝访问规则
     */
    public static CocoDataPermissionRule deny(String resource) {
        return new CocoDataPermissionRule(resource, CocoDataScope.DENY, Set.of());
    }

    /**
     * <p>
     * 判断当前规则是否允许访问全部数据。
     * </p>
     * @return 全部数据范围时返回 {@code true}
     */
    public boolean allData() {
        return this.scope == CocoDataScope.ALL;
    }

    /**
     * <p>
     * 判断当前规则是否拒绝访问数据。
     * </p>
     * @return 拒绝访问时返回 {@code true}
     */
    public boolean denied() {
        return this.scope == CocoDataScope.DENY;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
