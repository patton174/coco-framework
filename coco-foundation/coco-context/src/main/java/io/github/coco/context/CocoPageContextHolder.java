package io.github.coco.context;

import java.util.Optional;

/**
 * Coco 分页上下文持有器。
 * <p>
 * 使用 {@link ThreadLocal} 保存当前线程的分页上下文，由 Web 拦截器在请求入口设置，
 * 查询层通过 {@link #current()} 透明读取分页参数。支持通过 {@link #capture()} 实现
 * 跨线程上下文传播。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public final class CocoPageContextHolder {

    private static final ThreadLocal<CocoPageContext> PAGE_CONTEXT = new ThreadLocal<>();

    private CocoPageContextHolder() {
    }

    /**
     * <p>
     * 设置当前线程的分页上下文。
     * </p>
     * @param pageContext 分页上下文
     */
    public static void set(CocoPageContext pageContext) {
        if (pageContext == null) {
            clear();
        }
        else {
            PAGE_CONTEXT.set(pageContext);
        }
    }

    /**
     * <p>
     * 获取当前线程的分页上下文。
     * </p>
     * @return 分页上下文；未设置时为空
     */
    public static Optional<CocoPageContext> current() {
        return Optional.ofNullable(PAGE_CONTEXT.get());
    }

    /**
     * <p>
     * 清除当前线程的分页上下文。
     * </p>
     */
    public static void clear() {
        PAGE_CONTEXT.remove();
    }

    /**
     * <p>
     * 捕获当前线程的分页上下文快照，用于跨线程传播。
     * </p>
     * @return 上下文快照
     */
    public static CocoContextSnapshot capture() {
        Optional<CocoPageContext> captured = current();
        return () -> {
            Optional<CocoPageContext> previous = current();
            captured.ifPresentOrElse(CocoPageContextHolder::set, CocoPageContextHolder::clear);
            return () -> previous.ifPresentOrElse(CocoPageContextHolder::set, CocoPageContextHolder::clear);
        };
    }
}
