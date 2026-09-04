package io.github.coco.context;

import java.util.List;

/**
 * Coco 分页上下文。
 * <p>
 * 保存当前请求的分页和排序参数，由 Web 拦截器在请求入口设置，供查询层透明读取。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @param page 页码（从 1 开始）
 * @param size 每页大小
 * @param orders 排序字段列表
 * @author patton174
 * @since 1.1.0
 */
public record CocoPageContext(long page, long size, List<CocoSortOrder> orders) {

    /**
     * <p>
     * 创建分页上下文并校验合法性。
     * </p>
     * @param page 页码
     * @param size 每页大小
     * @param orders 排序字段列表
     */
    public CocoPageContext {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        orders = orders == null ? List.of() : List.copyOf(orders);
    }

    /**
     * <p>
     * 创建不带排序的分页上下文。
     * </p>
     * @param page 页码
     * @param size 每页大小
     */
    public CocoPageContext(long page, long size) {
        this(page, size, List.of());
    }
}
