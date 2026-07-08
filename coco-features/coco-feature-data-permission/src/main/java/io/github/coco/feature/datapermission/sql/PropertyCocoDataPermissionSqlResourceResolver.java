package io.github.coco.feature.datapermission.sql;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于配置属性的数据权限 SQL 资源解析器。
 * <p>
 * 按 {@code coco.data-permission.sql.resources.<resource>.tables} 将表名映射为业务资源标识。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class PropertyCocoDataPermissionSqlResourceResolver implements CocoDataPermissionSqlResourceResolver {

    private final CocoDataPermissionSqlProperties properties;

    /**
     * <p>
     * 创建基于配置属性的数据权限 SQL 资源解析器。
     * </p>
     * @param properties 数据权限 SQL 配置
     */
    public PropertyCocoDataPermissionSqlResourceResolver(CocoDataPermissionSqlProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> resolve(CocoDataPermissionSqlResourceContext context) {
        Objects.requireNonNull(context, "context must not be null");
        String tableName = normalize(context.table().getName());
        String fullyQualifiedName = normalize(context.table().getFullyQualifiedName());
        if (tableName.isEmpty() && fullyQualifiedName.isEmpty()) {
            return Optional.empty();
        }
        return this.properties.getResources().entrySet().stream()
                .filter(entry -> hasText(entry.getKey()))
                .filter(entry -> matches(entry, tableName, fullyQualifiedName))
                .map(entry -> entry.getKey().trim())
                .findFirst();
    }

    private static boolean matches(Map.Entry<String, CocoDataPermissionSqlResourceProperties> entry,
            String tableName, String fullyQualifiedName) {
        CocoDataPermissionSqlResourceProperties resource = entry.getValue();
        if (resource == null) {
            return false;
        }
        return resource.getTables().stream()
                .map(PropertyCocoDataPermissionSqlResourceResolver::normalize)
                .anyMatch(candidate -> !candidate.isEmpty()
                        && (candidate.equals(tableName) || candidate.equals(fullyQualifiedName)));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        normalized = strip(normalized, "`", "`");
        normalized = strip(normalized, "\"", "\"");
        normalized = strip(normalized, "[", "]");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String strip(String value, String prefix, String suffix) {
        if (value.startsWith(prefix) && value.endsWith(suffix) && value.length() > prefix.length() + suffix.length()) {
            return value.substring(prefix.length(), value.length() - suffix.length());
        }
        return value;
    }
}
