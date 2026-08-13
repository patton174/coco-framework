package io.github.coco.security.jwt;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Coco JWT Resource Server 自动配置。
 * <p>
 * 仅在 Servlet、Coco Web/Security 功能、Spring Security Resource Server/Nimbus 类存在且
 * {@code coco.security.jwt.enabled=true} 时启用。业务提供 {@link SecurityFilterChain} 后，模块的兜底链自动回退。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(before = CocoSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnClass(name = {
        "jakarta.servlet.Filter",
        "io.github.coco.feature.security.web.CocoSecurityWebFilter",
        "io.github.coco.feature.security.web.CocoWebSecurityContextResolver",
        "io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter",
        "org.springframework.security.config.annotation.web.builders.HttpSecurity",
        "org.springframework.security.core.Authentication",
        "org.springframework.security.core.GrantedAuthority",
        "org.springframework.security.core.context.SecurityContextHolder",
        "org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator",
        "org.springframework.security.oauth2.core.OAuth2TokenValidator",
        "org.springframework.security.oauth2.jwt.Jwt",
        "org.springframework.security.oauth2.jwt.JwtClaimValidator",
        "org.springframework.security.oauth2.jwt.JwtDecoder",
        "org.springframework.security.oauth2.jwt.JwtIssuerValidator",
        "org.springframework.security.oauth2.jwt.JwtTimestampValidator",
        "org.springframework.security.oauth2.jwt.JwtTypeValidator",
        "org.springframework.security.oauth2.jwt.NimbusJwtDecoder",
        "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter",
        "org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter",
        "org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter",
        "org.springframework.security.web.AuthenticationEntryPoint",
        "org.springframework.security.web.SecurityFilterChain",
        "org.springframework.security.web.access.AccessDeniedHandler"
})
@ConditionalOnProperty(prefix = "coco.security.jwt", name = "enabled", havingValue = "true")
@Import(CocoSecurityJwtSecurityConfiguration.class)
public class CocoSecurityJwtAutoConfiguration {
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnCocoFeature(CocoFeature.SECURITY)
@EnableConfigurationProperties(CocoSecurityJwtProperties.class)
class CocoSecurityJwtSecurityConfiguration {

    /**
     * 注册 JWT Resource Server 消息资源。
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoSecurityJwtMessageBundleRegistrar")
    CocoMessageBundleRegistrar cocoSecurityJwtMessageBundleRegistrar() {
        return registry -> registry.add("coco-security-jwt-messages");
    }

    /**
     * 创建默认 JWT 解码器并组合标准校验器。
     * @param properties JWT 配置
     * @return JWT 解码器
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder cocoJwtDecoder(CocoSecurityJwtProperties properties) {
        NimbusJwtDecoder decoder = properties.getJwkSetUri() == null
                ? NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri().toString()).build()
                : NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri().toString()).build();
        decoder.setJwtValidator(CocoJwtValidators.create(properties));
        return decoder;
    }

    /**
     * 创建默认 Spring Security 权限转换器。
     * @return 权限转换 SPI
     */
    @Bean
    @ConditionalOnMissingBean(CocoJwtAuthoritiesConverter.class)
    CocoJwtAuthoritiesConverter cocoJwtAuthoritiesConverter(CocoSecurityJwtProperties properties) {
        return new DefaultCocoJwtAuthoritiesConverter(properties);
    }

    /**
     * 创建默认 Coco 主体转换器。
     * @param properties JWT 配置
     * @return Coco 主体转换 SPI
     */
    @Bean
    @ConditionalOnMissingBean(CocoJwtPrincipalConverter.class)
    CocoJwtPrincipalConverter cocoJwtPrincipalConverter(CocoSecurityJwtProperties properties) {
        return new DefaultCocoJwtPrincipalConverter(properties);
    }

    /**
     * 创建默认 Spring Security JWT 认证转换器。
     * <p>
     * 该转换器只由本模块的兜底 {@link SecurityFilterChain} 使用；业务可通过同名 Bean
     * 替换其映射规则。JWT 的签名和声明校验始终由 Resource Server 的 {@link JwtDecoder}
     * 在调用该转换器之前完成。
     * </p>
     * @param authoritiesConverter JWT 权限转换 SPI
     * @param properties JWT 配置
     * @return JWT 认证转换器
     */
    @Bean(name = "cocoJwtAuthenticationConverter")
    @ConditionalOnMissingBean(name = "cocoJwtAuthenticationConverter")
    Converter<Jwt, ? extends AbstractAuthenticationToken> cocoJwtAuthenticationConverter(
            CocoJwtAuthoritiesConverter authoritiesConverter, CocoSecurityJwtProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter::convert);
        converter.setPrincipalClaimName(properties.getPrincipalIdClaim());
        return converter;
    }

    /**
     * 创建 Spring Security 到 Coco 安全上下文的解析器。
     * @param principalConverter Coco 主体转换 SPI
     * @return Coco Web 安全上下文解析器
     */
    @Bean
    @ConditionalOnMissingBean(CocoWebSecurityContextResolver.class)
    CocoWebSecurityContextResolver cocoJwtWebSecurityContextResolver(
            CocoJwtPrincipalConverter principalConverter) {
        return new CocoJwtWebSecurityContextResolver(principalConverter);
    }

    /**
     * 禁用 Coco Security 的容器级桥接过滤器；JWT 桥接必须在 Bearer JWT 已验证后运行。
     * @return 已禁用的同名注册，防止核心模块注册早于 Spring Security 的过滤器
     */
    @Bean(name = "cocoSecurityWebFilterRegistration")
    @ConditionalOnMissingBean(name = "cocoSecurityWebFilterRegistration")
    FilterRegistrationBean<CocoSecurityWebFilter> cocoJwtSecurityWebFilterRegistration() {
        FilterRegistrationBean<CocoSecurityWebFilter> registration = new FilterRegistrationBean<>();
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 创建 Coco Bearer 认证失败入口。
     * @param responseWriter Coco 过滤器异常响应写出器
     * @return 认证失败入口
     */
    @Bean(name = "cocoJwtAuthenticationEntryPoint")
    @ConditionalOnBean(CocoFilterExceptionResponseWriter.class)
    @ConditionalOnMissingBean(name = "cocoJwtAuthenticationEntryPoint")
    AuthenticationEntryPoint cocoJwtAuthenticationEntryPoint(CocoFilterExceptionResponseWriter responseWriter) {
        return new CocoJwtAuthenticationEntryPoint(responseWriter);
    }

    /**
     * 创建 Coco Bearer 访问拒绝处理器。
     * @param responseWriter Coco 过滤器异常响应写出器
     * @return 访问拒绝处理器
     */
    @Bean(name = "cocoJwtAccessDeniedHandler")
    @ConditionalOnBean(CocoFilterExceptionResponseWriter.class)
    @ConditionalOnMissingBean(name = "cocoJwtAccessDeniedHandler")
    AccessDeniedHandler cocoJwtAccessDeniedHandler(CocoFilterExceptionResponseWriter responseWriter) {
        return new CocoJwtAccessDeniedHandler(responseWriter);
    }

    /**
     * 创建供业务 SecurityFilterChain 复用的 Resource Server 接线器。
     * @param decoder JWT 解码器
     * @param authenticationConverter JWT 认证转换器
     * @param securityContextResolver Coco Web 安全上下文解析器
     * @param authenticationEntryPoint 认证失败入口
     * @param accessDeniedHandler 访问拒绝处理器
     * @return Resource Server 接线器
     */
    @Bean
    @ConditionalOnBean(CocoFilterExceptionResponseWriter.class)
    @ConditionalOnMissingBean
    CocoJwtResourceServerConfigurer cocoJwtResourceServerConfigurer(JwtDecoder decoder,
            @Qualifier("cocoJwtAuthenticationConverter")
            Converter<Jwt, ? extends AbstractAuthenticationToken> authenticationConverter,
            CocoWebSecurityContextResolver securityContextResolver,
            @Qualifier("cocoJwtAuthenticationEntryPoint") AuthenticationEntryPoint authenticationEntryPoint,
            @Qualifier("cocoJwtAccessDeniedHandler") AccessDeniedHandler accessDeniedHandler) {
        return new CocoJwtResourceServerConfigurer(decoder, authenticationConverter, securityContextResolver,
                authenticationEntryPoint, accessDeniedHandler);
    }

    /**
     * 在业务未提供 SecurityFilterChain 时创建拒绝匿名请求的安全兜底链。
     * @param http Spring Security 配置器
     * @param configurer Coco JWT 接线器
     * @return 安全过滤器链
     */
    @Bean(name = "cocoJwtSecurityFilterChain")
    @ConditionalOnBean(CocoJwtResourceServerConfigurer.class)
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain cocoJwtSecurityFilterChain(HttpSecurity http,
            CocoJwtResourceServerConfigurer configurer) {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        configurer.configure(http);
        try {
            return http.build();
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to build Coco JWT security filter chain", exception);
        }
    }
}
