package io.github.coco.api;

import java.util.List;

/**
 * Coco 通用分页结果。
 * <p>
 * 提供与持久层无关的分页数据结构，适用于所有 Coco Web 服务的分页响应。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-api}</li>
 * </ul>
 * @param items 当前页数据列表
 * @param total 总记录数
 * @param page 当前页码（从 1 开始）
 * @param size 每页大小
 * @param <T> 数据类型
 * @author patton174
 * @since 1.1.0
 */
public record CocoPage<T>(List<T> items, long total, long page, long size) {

    /**
     * <p>
     * 创建分页结果，并对数据列表做防御性拷贝。
     * </p>
     * @param items 当前页数据列表
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页大小
     */
    public CocoPage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * <p>
     * 计算总页数。
     * </p>
     * @return 总页数
     */
    public long totalPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }

    /**
     * <p>
     * 判断是否存在下一页。
     * </p>
     * @return 存在下一页时返回 {@code true}
     */
    public boolean hasNext() {
        return page < totalPages();
    }
}
