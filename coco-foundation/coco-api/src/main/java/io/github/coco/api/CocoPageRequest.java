package io.github.coco.api;

/**
 * Coco 分页请求参数。
 * <p>
 * 表示一次分页查询的页码和每页大小，页码从 1 开始。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-api}</li>
 * </ul>
 * @param page 页码（从 1 开始）
 * @param size 每页大小
 * @author patton174
 * @since 1.1.0
 */
public record CocoPageRequest(long page, long size) {

    /**
     * <p>
     * 创建分页请求参数并校验合法性。
     * </p>
     * @param page 页码
     * @param size 每页大小
     */
    public CocoPageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
    }

    /**
     * <p>
     * 创建分页请求参数。
     * </p>
     * @param page 页码（从 1 开始）
     * @param size 每页大小
     * @return 分页请求参数
     */
    public static CocoPageRequest of(long page, long size) {
        return new CocoPageRequest(page, size);
    }
}
