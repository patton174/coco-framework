package io.github.coco.feature.mybatisplus.pagination;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 MyBatis-Plus Entity 字段为可排序。
 * <p>
 * 框架在处理排序请求时，仅允许标记了此注解的字段参与 {@code ORDER BY} 子句。
 * 未标记的字段会被静默忽略，防止通过排序参数探测表结构或注入 SQL。
 * </p>
 * <p>
 * 注解的 {@link #value()} 为排序参数名（即前端传入的字段名），默认为空时使用
 * {@code @TableField} 的列名或 Java 字段名。
 * </p>
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
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CocoSortable {

    /**
     * <p>
     * 排序参数名。
     * </p>
     * <p>
     * 前端通过 {@code ?sort=value,asc} 传入此名称。为空时自动推导：
     * 优先取 {@code @TableField} 的列名，其次取 Java 字段名。
     * </p>
     * @return 排序参数名
     */
    String value() default "";
}
