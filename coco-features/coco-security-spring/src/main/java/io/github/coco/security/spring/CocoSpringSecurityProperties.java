package io.github.coco.security.spring;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Security Coco context bridge properties.
 *
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.security.spring")
public class CocoSpringSecurityProperties implements InitializingBean {

    private static final String DEFAULT_ROLE_PREFIX = "ROLE_";

    private boolean enabled;

    private String rolePrefix = DEFAULT_ROLE_PREFIX;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRolePrefix() {
        return this.rolePrefix;
    }

    public void setRolePrefix(String rolePrefix) {
        this.rolePrefix = rolePrefix;
    }

    @Override
    public void afterPropertiesSet() {
        if (!this.enabled) {
            return;
        }
        if (this.rolePrefix == null || this.rolePrefix.isBlank()) {
            throw new IllegalStateException("coco.security.spring.role-prefix must not be blank");
        }
        String normalized = this.rolePrefix.trim();
        if (normalized.length() > 128 || !normalized.chars().allMatch(CocoSpringSecurityProperties::isVisibleAscii)) {
            throw new IllegalStateException("coco.security.spring.role-prefix is invalid");
        }
        this.rolePrefix = normalized;
    }

    private static boolean isVisibleAscii(int character) {
        return character >= 0x21 && character <= 0x7e;
    }
}
