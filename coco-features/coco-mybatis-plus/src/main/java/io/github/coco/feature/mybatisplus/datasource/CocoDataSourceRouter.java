package io.github.coco.feature.mybatisplus.datasource;

import java.util.Optional;

/**
 * 动态数据源当前路由键决策器 SPI。
 * <p>
 * 默认从 {@link CocoDataSourceContextHolder} 读取当前线程栈顶的数据源键；
 * 业务或租户插件可通过自定义 {@code CocoDataSourceRouter} Bean 实现基于租户上下文或请求头的无侵入自动路由。
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
public interface CocoDataSourceRouter {

    /**
     * <p>
     * 返回当前执行上下文请求的数据源名称或分组名称；返回空时路由至默认主数据源。
     * </p>
     * @return 当前数据源路由键
     */
    Optional<String> currentDataSourceKey();
}
