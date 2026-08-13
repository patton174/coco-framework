package io.github.coco.feature.security.method;

/**
 * Coco 方法授权配置。
 * <p>
 * 绑定 {@code coco.security.method} 命名空间，控制 {@code @CocoAuthorize} 顾问是否注册。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
public class CocoSecurityMethodProperties {

    /** 是否启用注解方法授权。 */
    private boolean enabled = true;

    /**
     * 返回是否启用注解方法授权。
     * <p>
     * 默认启用；关闭后不会注册方法授权决策器、顾问或自动代理创建器。
     * </p>
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置是否启用注解方法授权。
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
