package io.github.coco.datapermission.context;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Coco 数据权限上下文。
 * <p>
 * 保存当前调用方可用的数据权限规则，供 SQL 拦截、查询构造或业务侧显式校验读取。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @param rules 数据权限规则集合
 * @author patton174
 * @since 1.0.0
 */
public record CocoDataPermissionContext(Set<CocoDataPermissionRule> rules) {

    /**
     * <p>
     * 创建数据权限上下文并复制规则集合。
     * </p>
     */
    public CocoDataPermissionContext {
        rules = Set.copyOf(rules == null ? Set.of() : rules);
    }

    /**
     * <p>
     * 创建数据权限上下文。
     * </p>
     * @param rules 数据权限规则集合
     * @return 数据权限上下文
     */
    public static CocoDataPermissionContext of(Set<CocoDataPermissionRule> rules) {
        return new CocoDataPermissionContext(rules);
    }

    /**
     * <p>
     * 创建空数据权限上下文。
     * </p>
     * @return 空数据权限上下文
     */
    public static CocoDataPermissionContext empty() {
        return new CocoDataPermissionContext(Set.of());
    }

    /**
     * <p>
     * 按资源标识返回数据权限规则。
     * </p>
     * @param resource 资源标识
     * @return 数据权限规则；不存在时为空
     */
    public Optional<CocoDataPermissionRule> rule(String resource) {
        if (resource == null || resource.isBlank()) {
            return Optional.empty();
        }
        String targetResource = resource.trim();
        return this.rules.stream()
                .filter(rule -> rule.resource().equals(targetResource))
                .findFirst();
    }

    /**
     * <p>
     * 将规则转换为资源索引。
     * </p>
     * @return 以资源标识为键的数据权限规则映射
     */
    public Map<String, CocoDataPermissionRule> rulesByResource() {
        return this.rules.stream()
                .collect(Collectors.toUnmodifiableMap(CocoDataPermissionRule::resource, Function.identity()));
    }
}
