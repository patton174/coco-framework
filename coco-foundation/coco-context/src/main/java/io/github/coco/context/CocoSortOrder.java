package io.github.coco.context;

import java.util.Objects;

/**
 * Coco 排序字段。
 * <p>
 * 表示单个排序维度，包含字段名和排序方向。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @param field 排序字段名
 * @param ascending 是否升序
 * @author patton174
 * @since 1.1.0
 */
public record CocoSortOrder(String field, boolean ascending) {

    public CocoSortOrder {
        Objects.requireNonNull(field, "field must not be null");
    }

    /**
     * <p>
     * 创建升序排序。
     * </p>
     * @param field 排序字段名
     * @return 升序排序字段
     */
    public static CocoSortOrder asc(String field) {
        return new CocoSortOrder(field, true);
    }

    /**
     * <p>
     * 创建降序排序。
     * </p>
     * @param field 排序字段名
     * @return 降序排序字段
     */
    public static CocoSortOrder desc(String field) {
        return new CocoSortOrder(field, false);
    }
}
