package io.github.coco.security.jwt;

import java.util.Objects;

import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 业务 {@link org.springframework.security.web.SecurityFilterChain} 的 Coco JWT 接线器。
 * <p>
 * 该类型只配置 OAuth2 Resource Server 组件和安全上下文桥接过滤器，不定义业务 URL 授权规则。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoJwtResourceServerConfigurer {

    private final JwtDecoder jwtDecoder;

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> authenticationConverter;

    private final CocoWebSecurityContextResolver securityContextResolver;

    private final AuthenticationEntryPoint authenticationEntryPoint;

    private final AccessDeniedHandler accessDeniedHandler;

    /**
     * 创建 Resource Server 接线器。
     * @param jwtDecoder JWT 解码器
     * @param authenticationConverter JWT 到 Spring Security Authentication 的转换器
     * @param securityContextResolver Coco Web 安全上下文解析器
     * @param authenticationEntryPoint Bearer 认证失败入口
     * @param accessDeniedHandler Bearer 访问拒绝处理器
     */
    public CocoJwtResourceServerConfigurer(JwtDecoder jwtDecoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> authenticationConverter,
            CocoWebSecurityContextResolver securityContextResolver,
            AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder must not be null");
        this.authenticationConverter = Objects.requireNonNull(authenticationConverter,
                "authenticationConverter must not be null");
        this.securityContextResolver = Objects.requireNonNull(securityContextResolver,
                "securityContextResolver must not be null");
        this.authenticationEntryPoint = Objects.requireNonNull(authenticationEntryPoint,
                "authenticationEntryPoint must not be null");
        this.accessDeniedHandler = Objects.requireNonNull(accessDeniedHandler,
                "accessDeniedHandler must not be null");
    }

    /**
     * 将 Coco JWT 适配应用到业务 {@link HttpSecurity}。
     * @param http 业务 HttpSecurity
     */
    public void configure(HttpSecurity http) {
        HttpSecurity checkedHttp = Objects.requireNonNull(http, "http must not be null");
        checkedHttp.oauth2ResourceServer(resourceServer -> resourceServer
                .authenticationEntryPoint(this.authenticationEntryPoint)
                .accessDeniedHandler(this.accessDeniedHandler)
                .jwt(jwt -> jwt
                        .decoder(this.jwtDecoder)
                        .jwtAuthenticationConverter(this.authenticationConverter)));
        checkedHttp.addFilterAfter(new CocoSecurityWebFilter(this.securityContextResolver),
                BearerTokenAuthenticationFilter.class);
    }

}
