package io.github.coco.security.web;

/**
 * Coco Web 可信请求头安全上下文适配配置。
 * <p>
 * 该适配器只适合由可信网关、认证过滤器或业务基础设施写入请求头的场景。默认关闭，避免直接信任外部客户端输入。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoSecurityWebHeaderProperties {

    /**
     * 是否启用可信请求头安全上下文适配。
     */
    private boolean enabled;

    /**
     * 主体标识请求头名称。
     */
    private String principalIdHeaderName = "X-Coco-Principal-Id";

    /**
     * 主体显示名称请求头名称。
     */
    private String principalNameHeaderName = "X-Coco-Principal-Name";

    /**
     * 角色集合请求头名称。
     */
    private String rolesHeaderName = "X-Coco-Roles";

    /**
     * 权限集合请求头名称。
     */
    private String permissionsHeaderName = "X-Coco-Permissions";

    /**
     * 角色和权限请求头的分隔符。
     */
    private String authorityDelimiter = ",";

    /**
     * <p>
     * 返回是否启用可信请求头安全上下文适配。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用可信请求头安全上下文适配。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回主体标识请求头名称。
     * </p>
     * @return 主体标识请求头名称
     */
    public String getPrincipalIdHeaderName() {
        return this.principalIdHeaderName;
    }

    /**
     * <p>
     * 设置主体标识请求头名称。
     * </p>
     * @param principalIdHeaderName 主体标识请求头名称
     */
    public void setPrincipalIdHeaderName(String principalIdHeaderName) {
        this.principalIdHeaderName = normalize(principalIdHeaderName, "X-Coco-Principal-Id");
    }

    /**
     * <p>
     * 返回主体显示名称请求头名称。
     * </p>
     * @return 主体显示名称请求头名称
     */
    public String getPrincipalNameHeaderName() {
        return this.principalNameHeaderName;
    }

    /**
     * <p>
     * 设置主体显示名称请求头名称。
     * </p>
     * @param principalNameHeaderName 主体显示名称请求头名称
     */
    public void setPrincipalNameHeaderName(String principalNameHeaderName) {
        this.principalNameHeaderName = normalize(principalNameHeaderName, "X-Coco-Principal-Name");
    }

    /**
     * <p>
     * 返回角色集合请求头名称。
     * </p>
     * @return 角色集合请求头名称
     */
    public String getRolesHeaderName() {
        return this.rolesHeaderName;
    }

    /**
     * <p>
     * 设置角色集合请求头名称。
     * </p>
     * @param rolesHeaderName 角色集合请求头名称
     */
    public void setRolesHeaderName(String rolesHeaderName) {
        this.rolesHeaderName = normalize(rolesHeaderName, "X-Coco-Roles");
    }

    /**
     * <p>
     * 返回权限集合请求头名称。
     * </p>
     * @return 权限集合请求头名称
     */
    public String getPermissionsHeaderName() {
        return this.permissionsHeaderName;
    }

    /**
     * <p>
     * 设置权限集合请求头名称。
     * </p>
     * @param permissionsHeaderName 权限集合请求头名称
     */
    public void setPermissionsHeaderName(String permissionsHeaderName) {
        this.permissionsHeaderName = normalize(permissionsHeaderName, "X-Coco-Permissions");
    }

    /**
     * <p>
     * 返回角色和权限请求头的分隔符。
     * </p>
     * @return 角色和权限请求头的分隔符
     */
    public String getAuthorityDelimiter() {
        return this.authorityDelimiter;
    }

    /**
     * <p>
     * 设置角色和权限请求头的分隔符。
     * </p>
     * @param authorityDelimiter 角色和权限请求头的分隔符
     */
    public void setAuthorityDelimiter(String authorityDelimiter) {
        this.authorityDelimiter = normalize(authorityDelimiter, ",");
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
