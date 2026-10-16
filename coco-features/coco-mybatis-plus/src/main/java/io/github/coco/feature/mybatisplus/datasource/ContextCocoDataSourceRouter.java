package io.github.coco.feature.mybatisplus.datasource;

import java.util.Optional;

/**
 * 基于 {@link CocoDataSourceContextHolder} 线程栈的默认数据源路由决策器。
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
public final class ContextCocoDataSourceRouter implements CocoDataSourceRouter {

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> currentDataSourceKey() {
        return CocoDataSourceContextHolder.current();
    }
}
