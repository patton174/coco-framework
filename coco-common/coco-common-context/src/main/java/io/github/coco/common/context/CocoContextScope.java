package io.github.coco.common.context;

/**
 * Coco 上下文作用域。
 * <p>
 * 通过 {@link AutoCloseable} 语义表达一次临时上下文恢复范围，调用方可以使用 try-with-resources
 * 确保执行结束后恢复进入作用域前的线程上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-context}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoContextScope extends AutoCloseable {

    /**
     * <p>
     * 关闭当前上下文作用域，并恢复进入作用域前的线程上下文。
     * </p>
     */
    @Override
    void close();

    /**
     * <p>
     * 返回空作用域。
     * </p>
     * @return 空作用域
     */
    static CocoContextScope noop() {
        return () -> {
        };
    }
}
