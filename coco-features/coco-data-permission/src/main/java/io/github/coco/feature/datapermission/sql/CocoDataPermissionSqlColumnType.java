package io.github.coco.feature.datapermission.sql;

/**
 * Coco 数据权限 SQL 列值类型。
 * <p>
 * 默认谓词生成器根据该类型把数据权限规则值转换为 SQL 字面量，避免数值列被错误生成为字符串字面量。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoDataPermissionSqlColumnType {

    /**
     * 文本列，权限值生成字符串字面量。
     */
    STRING,

    /**
     * 整数列，权限值生成长整数字面量。
     */
    LONG
}
