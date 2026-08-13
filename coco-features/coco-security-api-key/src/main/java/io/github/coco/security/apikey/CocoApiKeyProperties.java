package io.github.coco.security.apikey;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco API Key 认证配置。
 * <p>
 * 仅接受 API Key 的 SHA-256 摘要，不提供任何明文 Key 配置字段。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.security.api-key")
public class CocoApiKeyProperties implements InitializingBean {

    static final int DEFAULT_MAX_KEY_LENGTH = 512;

    static final int MAX_ALLOWED_KEY_LENGTH = 4096;

    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");

    private static final Pattern SHA_256 = Pattern.compile("^[0-9A-Fa-f]{64}$");

    private boolean enabled;

    private String headerName = "X-API-Key";

    private boolean required = true;

    private int maxKeyLength = DEFAULT_MAX_KEY_LENGTH;

    private Map<String, Credential> credentials = new LinkedHashMap<>();

    /**
     * 返回是否显式启用 API Key 认证。
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置是否显式启用 API Key 认证。
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回承载 API Key 的请求头名称。
     * @return 请求头名称
     */
    public String getHeaderName() {
        return this.headerName;
    }

    /**
     * 设置承载 API Key 的请求头名称。
     * @param headerName 请求头名称
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    /**
     * 返回缺失 API Key 时是否拒绝请求。
     * @return 必填时返回 {@code true}
     */
    public boolean isRequired() {
        return this.required;
    }

    /**
     * 设置缺失 API Key 时是否拒绝请求。
     * @param required 是否必填
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * 返回单个 API Key 的最大字符数。
     * @return 最大字符数
     */
    public int getMaxKeyLength() {
        return this.maxKeyLength;
    }

    /**
     * 设置单个 API Key 的最大字符数。
     * @param maxKeyLength 最大字符数
     */
    public void setMaxKeyLength(int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
    }

    /**
     * 返回凭据配置。
     * <p>
     * Map 的键仅用于绑定配置，不会写入主体属性。
     * </p>
     * @return 凭据配置
     */
    public Map<String, Credential> getCredentials() {
        return Map.copyOf(this.credentials);
    }

    /**
     * 设置凭据配置。
     * @param credentials 凭据配置
     */
    public void setCredentials(Map<String, Credential> credentials) {
        this.credentials = credentials == null ? new LinkedHashMap<>() : new LinkedHashMap<>(credentials);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() {
        if (!this.enabled) {
            return;
        }
        this.headerName = validateHeaderName(this.headerName);
        if (this.maxKeyLength <= 0 || this.maxKeyLength > MAX_ALLOWED_KEY_LENGTH) {
            throw new IllegalStateException("coco.security.api-key.max-key-length is outside the allowed range");
        }
        LinkedHashMap<String, Credential> validated = new LinkedHashMap<>();
        for (Map.Entry<String, Credential> entry : this.credentials.entrySet()) {
            String credentialId = requireText(entry.getKey(), "credential id");
            Credential credential = entry.getValue();
            if (credential == null) {
                throw new IllegalStateException("coco.security.api-key.credentials contains an invalid credential");
            }
            credential.validate();
            validated.put(credentialId, credential);
        }
        this.credentials = validated;
    }

    private static String validateHeaderName(String value) {
        String headerName = requireText(value, "header-name");
        if (!HEADER_NAME.matcher(headerName).matches()) {
            throw new IllegalStateException("coco.security.api-key.header-name is invalid");
        }
        String normalized = headerName.toLowerCase(Locale.ROOT);
        if ("authorization".equals(normalized) || "cookie".equals(normalized)
                || "proxy-authorization".equals(normalized)) {
            throw new IllegalStateException("coco.security.api-key.header-name is reserved");
        }
        return headerName;
    }

    private static String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("coco.security.api-key." + property + " must not be blank");
        }
        return value.trim();
    }

    /**
     * API Key 摘要与主体映射配置。
     * <p>
     * 不包含原始 Key；{@code sha256} 是唯一可配置的认证材料。
     * </p>
     */
    public static class Credential {

        private String sha256;

        private String principalId;

        private String principalName;

        private Set<String> roles = new LinkedHashSet<>();

        private Set<String> permissions = new LinkedHashSet<>();

        private Map<String, Object> attributes = new LinkedHashMap<>();

        public String getSha256() {
            return this.sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }

        public String getPrincipalId() {
            return this.principalId;
        }

        public void setPrincipalId(String principalId) {
            this.principalId = principalId;
        }

        public String getPrincipalName() {
            return this.principalName;
        }

        public void setPrincipalName(String principalName) {
            this.principalName = principalName;
        }

        public Set<String> getRoles() {
            return Set.copyOf(this.roles);
        }

        public void setRoles(Collection<String> roles) {
            this.roles = normalizeValues(roles);
        }

        public Set<String> getPermissions() {
            return Set.copyOf(this.permissions);
        }

        public void setPermissions(Collection<String> permissions) {
            this.permissions = normalizeValues(permissions);
        }

        public Map<String, Object> getAttributes() {
            return Map.copyOf(this.attributes);
        }

        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        }

        private void validate() {
            if (this.sha256 == null || !SHA_256.matcher(this.sha256).matches()) {
                throw new IllegalStateException("coco.security.api-key.credentials contains an invalid sha256 value");
            }
            this.sha256 = this.sha256.toLowerCase(Locale.ROOT);
            this.principalId = requireText(this.principalId, "credentials principal-id");
            this.principalName = this.principalName == null || this.principalName.isBlank()
                    ? null : this.principalName.trim();
            this.roles = normalizeValues(this.roles);
            this.permissions = normalizeValues(this.permissions);
            this.attributes = new LinkedHashMap<>(this.attributes == null ? Map.of() : this.attributes);
        }

        private static LinkedHashSet<String> normalizeValues(Collection<String> values) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (values != null) {
                values.stream().filter(value -> value != null && !value.isBlank())
                        .map(String::trim).forEach(normalized::add);
            }
            return normalized;
        }
    }
}
