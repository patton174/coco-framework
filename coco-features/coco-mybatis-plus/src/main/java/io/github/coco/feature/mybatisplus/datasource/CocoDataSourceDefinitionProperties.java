package io.github.coco.feature.mybatisplus.datasource;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;

/**
 * 单个数据源的配置属性项。
 * <p>
 * 对应 {@code coco.mybatis-plus.datasource.sources.<name>} 下的连接与池化参数。
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
public class CocoDataSourceDefinitionProperties {

    private String group;

    private String url;

    private String username;

    private String password;

    private String driverClassName;

    private Class<? extends DataSource> type;

    private Map<String, String> properties = new LinkedHashMap<>();

    /**
     * <p>
     * 返回显式所属的数据源分组名称。
     * </p>
     * @return 分组名称
     */
    public String getGroup() {
        return this.group;
    }

    /**
     * <p>
     * 设置显式所属的数据源分组名称。
     * </p>
     * @param group 分组名称
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * <p>
     * 返回 JDBC 连接 URL。
     * </p>
     * @return JDBC 连接 URL
     */
    public String getUrl() {
        return this.url;
    }

    /**
     * <p>
     * 设置 JDBC 连接 URL。
     * </p>
     * @param url JDBC 连接 URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * <p>
     * 返回数据库用户名。
     * </p>
     * @return 数据库用户名
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * <p>
     * 设置数据库用户名。
     * </p>
     * @param username 数据库用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * <p>
     * 返回数据库密码。
     * </p>
     * @return 数据库密码
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * <p>
     * 设置数据库密码。
     * </p>
     * @param password 数据库密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * <p>
     * 返回 JDBC 驱动类名。
     * </p>
     * @return JDBC 驱动类名
     */
    public String getDriverClassName() {
        return this.driverClassName;
    }

    /**
     * <p>
     * 设置 JDBC 驱动类名。
     * </p>
     * @param driverClassName JDBC 驱动类名
     */
    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    /**
     * <p>
     * 返回数据源连接池实现类型。
     * </p>
     * @return 数据源连接池实现类型
     */
    public Class<? extends DataSource> getType() {
        return this.type;
    }

    /**
     * <p>
     * 设置数据源连接池实现类型。
     * </p>
     * @param type 数据源连接池实现类型
     */
    public void setType(Class<? extends DataSource> type) {
        this.type = type;
    }

    /**
     * <p>
     * 返回附加连接池配置属性副本。
     * </p>
     * @return 附加属性映射
     */
    public Map<String, String> getProperties() {
        return new LinkedHashMap<>(this.properties);
    }

    /**
     * <p>
     * 设置附加连接池配置属性。
     * </p>
     * @param properties 附加属性映射
     */
    public void setProperties(Map<String, String> properties) {
        this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
    }

    /**
     * <p>
     * 转换为不可变的 {@link CocoDataSourceDefinition}。
     * </p>
     * @param name 数据源名称
     * @return 不可变的数据源定义
     */
    public CocoDataSourceDefinition toDefinition(String name) {
        return new CocoDataSourceDefinition(
                name,
                this.group,
                this.url,
                this.username,
                this.password,
                this.driverClassName,
                this.type,
                this.properties);
    }
}
