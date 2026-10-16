package io.github.coco.feature.mybatisplus.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * 声明方法或类型执行时绑定的目标数据源名称、分组名称或 SpEL 表达式。
 * <p>
 * 支持标注在业务类、业务方法、接口方法以及 MyBatis Mapper 接口与方法上；方法级声明优先于类型级声明。
 * 当表达式以 {@code #} 或 {@code #{...}} 开头时，使用 {@link CocoDataSourceKeyResolver} 动态求值。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CocoDataSource {

    /**
     * <p>
     * 目标数据源名称、分组名或 SpEL 表达式，与 {@link #key()} 互为别名。
     * </p>
     * @return 数据源路由键表达式
     */
    @AliasFor("key")
    String value() default "";

    /**
     * <p>
     * 目标数据源名称、分组名或 SpEL 表达式，与 {@link #value()} 互为别名。
     * </p>
     * @return 数据源路由键表达式
     */
    @AliasFor("value")
    String key() default "";
}
