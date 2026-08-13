package io.github.coco.security.jwt;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco JWT Resource Server 配置。
 * <p>
 * 仅描述标准 JWT 解码、校验和主体声明映射，不承载用户、角色、菜单或租户模型。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.security.jwt")
public class CocoSecurityJwtProperties implements InitializingBean {

    private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(60);

    private boolean enabled;

    private URI issuerUri;

    private URI jwkSetUri;

    private Set<String> audiences = new LinkedHashSet<>();

    private Duration clockSkew = DEFAULT_CLOCK_SKEW;

    private String principalIdClaim = "sub";

    private String principalNameClaim = "name";

    private String authoritiesClaim = "scope";

    private String authoritiesPrefix = "SCOPE_";

    private Set<String> principalAttributeClaims = new LinkedHashSet<>();

    /**
     * 返回是否显式启用 JWT Resource Server 适配。
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置是否显式启用 JWT Resource Server 适配。
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 OIDC/OAuth2 issuer URI。
     * @return issuer URI；未配置时为空
     */
    public URI getIssuerUri() {
        return this.issuerUri;
    }

    /**
     * 设置 OIDC/OAuth2 issuer URI。
     * @param issuerUri issuer URI
     */
    public void setIssuerUri(URI issuerUri) {
        this.issuerUri = issuerUri;
    }

    /**
     * 返回 JWK Set URI。
     * @return JWK Set URI；未配置时为空
     */
    public URI getJwkSetUri() {
        return this.jwkSetUri;
    }

    /**
     * 设置 JWK Set URI。
     * @param jwkSetUri JWK Set URI
     */
    public void setJwkSetUri(URI jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    /**
     * 返回令牌至少必须包含其中一个值的 audience 白名单。
     * @return audience 白名单
     */
    public Set<String> getAudiences() {
        return Set.copyOf(this.audiences);
    }

    /**
     * 设置令牌至少必须包含其中一个值的 audience 白名单。
     * @param audiences audience 白名单
     */
    public void setAudiences(Collection<String> audiences) {
        this.audiences = normalizeValues(audiences);
    }

    /**
     * 返回时间声明校验允许的时钟偏差。
     * @return 时钟偏差
     */
    public Duration getClockSkew() {
        return this.clockSkew;
    }

    /**
     * 设置时间声明校验允许的时钟偏差。
     * @param clockSkew 时钟偏差
     */
    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew == null ? DEFAULT_CLOCK_SKEW : clockSkew;
    }

    /**
     * 返回映射 Coco 主体标识的声明名。
     * @return 主体标识声明名
     */
    public String getPrincipalIdClaim() {
        return this.principalIdClaim;
    }

    /**
     * 设置映射 Coco 主体标识的声明名。
     * @param principalIdClaim 主体标识声明名
     */
    public void setPrincipalIdClaim(String principalIdClaim) {
        this.principalIdClaim = principalIdClaim;
    }

    /**
     * 返回映射 Coco 主体名称的声明名。
     * @return 主体名称声明名
     */
    public String getPrincipalNameClaim() {
        return this.principalNameClaim;
    }

    /**
     * 设置映射 Coco 主体名称的声明名。
     * @param principalNameClaim 主体名称声明名
     */
    public void setPrincipalNameClaim(String principalNameClaim) {
        this.principalNameClaim = principalNameClaim;
    }

    /**
     * 返回读取权限的 JWT 声明名称。
     * @return 权限声明名称
     */
    public String getAuthoritiesClaim() {
        return this.authoritiesClaim;
    }

    /**
     * 设置读取权限的 JWT 声明名称。
     * @param authoritiesClaim 权限声明名称
     */
    public void setAuthoritiesClaim(String authoritiesClaim) {
        this.authoritiesClaim = authoritiesClaim;
    }

    /**
     * 返回映射到 Spring Security 的权限前缀。
     * @return 权限前缀
     */
    public String getAuthoritiesPrefix() {
        return this.authoritiesPrefix;
    }

    /**
     * 设置映射到 Spring Security 的权限前缀。
     * @param authoritiesPrefix 权限前缀
     */
    public void setAuthoritiesPrefix(String authoritiesPrefix) {
        this.authoritiesPrefix = authoritiesPrefix;
    }

    /**
     * 返回允许写入 Coco 主体 attributes 的 JWT 声明白名单。
     * <p>
     * 默认空集合，不传播任何 JWT 声明。
     * </p>
     * @return 主体属性声明白名单
     */
    public Set<String> getPrincipalAttributeClaims() {
        return Set.copyOf(this.principalAttributeClaims);
    }

    /**
     * 设置允许写入 Coco 主体 attributes 的 JWT 声明白名单。
     * @param principalAttributeClaims 主体属性声明白名单
     */
    public void setPrincipalAttributeClaims(Collection<String> principalAttributeClaims) {
        this.principalAttributeClaims = normalizeValues(principalAttributeClaims);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() {
        if (!this.enabled) {
            return;
        }
        if (this.issuerUri == null && this.jwkSetUri == null) {
            throw new IllegalStateException(
                    "coco.security.jwt.issuer-uri or coco.security.jwt.jwk-set-uri is required when enabled");
        }
        validateAbsoluteUri(this.issuerUri, "issuer-uri");
        validateAbsoluteUri(this.jwkSetUri, "jwk-set-uri");
        if (this.clockSkew.isNegative()) {
            throw new IllegalStateException("coco.security.jwt.clock-skew must not be negative");
        }
        this.principalIdClaim = requireText(this.principalIdClaim, "principal-id-claim");
        this.principalNameClaim = requireText(this.principalNameClaim, "principal-name-claim");
        this.authoritiesClaim = requireText(this.authoritiesClaim, "authorities-claim");
        this.authoritiesPrefix = this.authoritiesPrefix == null ? "" : this.authoritiesPrefix.trim();
        this.audiences = normalizeValues(this.audiences);
        this.principalAttributeClaims = normalizeValues(this.principalAttributeClaims);
        validateAttributeClaims(this.principalAttributeClaims);
    }

    private static void validateAbsoluteUri(URI uri, String name) {
        if (uri != null && !uri.isAbsolute()) {
            throw new IllegalStateException("coco.security.jwt." + name + " must be an absolute URI");
        }
    }

    private static LinkedHashSet<String> normalizeValues(Collection<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(normalized::add);
        return normalized;
    }

    private static void validateAttributeClaims(Set<String> claimNames) {
        for (String claimName : claimNames) {
            String normalized = claimName.toLowerCase(Locale.ROOT);
            if ("token".equals(normalized) || "access_token".equals(normalized)
                    || "id_token".equals(normalized) || "refresh_token".equals(normalized)) {
                throw new IllegalStateException(
                        "coco.security.jwt.principal-attribute-claims must not include token values");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("coco.security.jwt." + name + " must not be blank");
        }
        return value.trim();
    }
}
