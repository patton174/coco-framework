package io.github.coco.feature.mybatisplus.datasource;

import java.util.Map;
import javax.sql.DataSource;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;

/**
 * 不可变的数据源定义描述。
 * <p>
 * 封装单个物理数据源的名称、所属分组、连接信息、连接池类型及扩展属性，供 {@link CocoDataSourceCreator} 构造实例。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 *
 * @param name 数据源唯一名称
 * @param group 显式指定的数据源分组名称（为空时可按分隔符自动推导）
 * @param url JDBC 连接地址
 * @param username 数据库用户名
 * @param password 数据库密码
 * @param driverClassName JDBC 驱动类名
 * @param type 数据源实现类型（如 {@code HikariDataSource}）
 * @param properties 附加连接池或驱动属性
 * @author patton174
 * @since 1.0.0
 */
public record CocoDataSourceDefinition(
        String name,
        String group,
        String url,
        String username,
        String password,
        String driverClassName,
        Class<? extends DataSource> type,
        Map<String, String> properties) {

    /**
     * <p>
     * 规范化并校验数据源定义。
     * </p>
     */
    public CocoDataSourceDefinition {
        if (name == null || name.isBlank()) {
            throw CocoMybatisPlusErrorCode.INVALID_DATASOURCE_KEY.request();
        }
        name = name.trim();
        group = (group == null || group.isBlank()) ? null : group.trim();
        url = (url == null || url.isBlank()) ? null : url.trim();
        driverClassName = (driverClassName == null || driverClassName.isBlank()) ? null : driverClassName.trim();
        properties = (properties == null || properties.isEmpty()) ? Map.of() : Map.copyOf(properties);
    }

    /**
     * <p>
     * 快速构造基础 JDBC 数据源定义。
     * </p>
     * @param name 数据源名称
     * @param url JDBC 连接地址
     * @param username 用户名
     * @param password 密码
     * @return 数据源定义
     */
    public static CocoDataSourceDefinition of(String name, String url, String username, String password) {
        return new CocoDataSourceDefinition(name, null, url, username, password, null, null, Map.of());
    }

    /**
     * <p>
     * 返回不包含密码与附加属性取值的描述文本，避免凭据进入日志。
     * </p>
     * @return 脱敏后的描述文本
     */
    @Override
    public String toString() {
        return "CocoDataSourceDefinition[name=" + this.name + ", group=" + this.group + ", url=" + this.url
                + ", username=" + this.username + ", password=" + (this.password == null ? "null" : "******")
                + ", driverClassName=" + this.driverClassName + ", type=" + this.type
                + ", propertyKeys=" + this.properties.keySet() + "]";
    }
}
