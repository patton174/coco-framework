package io.github.coco.security.jwt;

import java.util.Collection;

import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 已验证 JWT 到 Coco 安全主体的转换 SPI。
 * <p>
 * 该 SPI 只负责协议声明适配，不定义用户、角色、菜单或租户领域模型。
 * </p>

 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoJwtPrincipalConverter {

    /**
     * 将已验证 JWT 和 Spring Security 权限转换为 Coco 安全主体。
     * @param jwt 已验证 JWT
     * @param authorities Spring Security 权限
     * @return Coco 安全主体
     */
    CocoSecurityPrincipal convert(Jwt jwt, Collection<? extends GrantedAuthority> authorities);
}
