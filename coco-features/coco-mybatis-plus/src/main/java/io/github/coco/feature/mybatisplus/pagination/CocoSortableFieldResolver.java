package io.github.coco.feature.mybatisplus.pagination;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.baomidou.mybatisplus.annotation.TableField;

/**
 * Coco 可排序字段解析器。
 * <p>
 * 扫描 Entity 类中标记了 {@link CocoSortable} 的字段，构建排序参数名到数据库列名的映射。
 * 解析结果按 Entity 类缓存，避免重复反射开销。
 * </p>
 * <p>
 * 映射规则：
 * </p>
 * <ol>
 *   <li>排序参数名（key）：{@link CocoSortable#value()} 非空时使用，否则使用 Java 字段名</li>
 *   <li>数据库列名（value）：{@link TableField#value()} 非空时使用，否则使用 Java 字段名</li>
 * </ol>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
final class CocoSortableFieldResolver {

    private static final ConcurrentHashMap<Class<?>, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private CocoSortableFieldResolver() {
    }

    /**
     * <p>
     * 解析 Entity 类的可排序字段映射。
     * </p>
     * @param entityClass Entity 类
     * @return 排序参数名 → 数据库列名的不可变映射；无 {@link CocoSortable} 注解时返回空映射
     */
    static Map<String, String> resolve(Class<?> entityClass) {
        if (entityClass == null) {
            return Map.of();
        }
        return CACHE.computeIfAbsent(entityClass, CocoSortableFieldResolver::scan);
    }

    private static Map<String, String> scan(Class<?> entityClass) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (Class<?> current = entityClass; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                CocoSortable sortable = field.getAnnotation(CocoSortable.class);
                if (sortable == null) {
                    continue;
                }
                String paramName = sortable.value().isBlank() ? field.getName() : sortable.value().trim();
                String columnName = resolveColumnName(field);
                mapping.putIfAbsent(paramName, columnName);
            }
        }
        return mapping.isEmpty() ? Map.of() : Collections.unmodifiableMap(mapping);
    }

    private static String resolveColumnName(Field field) {
        TableField tableField = field.getAnnotation(TableField.class);
        if (tableField != null && !tableField.value().isBlank()) {
            return tableField.value().trim();
        }
        return field.getName();
    }
}
