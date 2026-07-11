package io.github.coco.datapermission.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * Coco 数据权限 SQL 资源配置。
 * <p>
 * 定义一个业务资源关联的数据表集合，以及默认谓词生成器需要使用的数据范围列名。
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
public class CocoDataPermissionSqlResourceProperties {

    private List<String> tables = new ArrayList<>();

    private String column;

    private CocoDataPermissionSqlColumnType columnType = CocoDataPermissionSqlColumnType.STRING;

    /**
     * <p>
     * 返回资源关联的数据表名称。
     * </p>
     * @return 数据表名称集合
     */
    public List<String> getTables() {
        return this.tables;
    }

    /**
     * <p>
     * 设置资源关联的数据表名称。
     * </p>
     * @param tables 数据表名称集合
     */
    public void setTables(List<String> tables) {
        this.tables = tables == null ? new ArrayList<>() : new ArrayList<>(tables);
    }

    /**
     * <p>
     * 返回默认谓词生成器使用的数据范围列名。
     * </p>
     * @return 数据范围列名
     */
    public String getColumn() {
        return this.column;
    }

    /**
     * <p>
     * 设置默认谓词生成器使用的数据范围列名。
     * </p>
     * @param column 数据范围列名
     */
    public void setColumn(String column) {
        this.column = column == null || column.isBlank() ? null : column.trim();
    }

    /**
     * <p>
     * 返回默认谓词生成器使用的数据范围列值类型。
     * </p>
     * @return 数据范围列值类型
     */
    public CocoDataPermissionSqlColumnType getColumnType() {
        return this.columnType;
    }

    /**
     * <p>
     * 设置默认谓词生成器使用的数据范围列值类型。
     * </p>
     * @param columnType 数据范围列值类型
     */
    public void setColumnType(CocoDataPermissionSqlColumnType columnType) {
        this.columnType = columnType == null ? CocoDataPermissionSqlColumnType.STRING : columnType;
    }
}
