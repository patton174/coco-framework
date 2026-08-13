package io.github.coco.security.jwt;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JWT 声明到 Spring Security 权限的转换 SPI。
 * <p>
 * 默认实现委托 Spring Security 的 {@code JwtGrantedAuthoritiesConverter}，业务可提供同类型 Bean 替换。
 * </p>

 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoJwtAuthoritiesConverter {

    /**
     * 将 JWT 声明转换为 Spring Security 权限。
     * @param jwt 已验证 JWT
     * @return 权限集合
     */
    Collection<GrantedAuthority> convert(Jwt jwt);
}
