package io.github.coco.feature.mybatisplus.datasource;

import java.lang.reflect.Method;

/**
 * {@link CocoDataSource} 路由键解析器 SPI。
 * <p>
 * 负责将注解声明的静态字面量或动态表达式（如 SpEL）解析为最终的数据源名称或分组名称。
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
@FunctionalInterface
public interface CocoDataSourceKeyResolver {

    /**
     * <p>
     * 根据方法调用上下文解析目标数据源路由键。
     * </p>
     * @param keyExpression 注解声明的原始键或表达式
     * @param target 目标实例（接口代理场景可能为代理对象或 {@code null}）
     * @param method 当前调用的方法
     * @param arguments 方法入参
     * @return 解析后的非空数据源路由键
     */
    String resolve(String keyExpression, Object target, Method method, Object[] arguments);
}
