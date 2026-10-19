package io.github.coco.feature.mybatisplus.datasource;

/**
 * 数据源上下文作用域。
 * <p>
 * 配合 {@code try-with-resources} 在退出作用域时自动弹出当前压栈的数据源键并恢复上一层路由状态。
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
public interface CocoDataSourceScope extends AutoCloseable {

    /**
     * <p>
     * 结束当前数据源作用域并恢复上一层上下文。
     * </p>
     */
    @Override
    void close();
}
