package io.github.coco.feature.web;

import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.signature.CocoSignatureFilter;
import io.github.coco.feature.web.signature.CocoSignatureSecretResolver;
import io.github.coco.feature.web.signature.CocoSignatureVerifier;
import io.github.coco.feature.web.signature.HmacSha256CocoSignatureVerifier;
import io.github.coco.feature.web.signature.PropertiesCocoSignatureSecretResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Coco Web 签名自动配置�? * <p>
 * 注册签名密钥解析器、签名验证器和签名过滤器�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
public class CocoWebSignatureAutoConfiguration {

    /**
     * <p>
     * 创建默认 Coco 请求签名密钥解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return 请求签名密钥解析�?     */
    @Bean
    @ConditionalOnMissingBean
    public CocoSignatureSecretResolver cocoSignatureSecretResolver(CocoWebProperties properties) {
        return new PropertiesCocoSignatureSecretResolver(properties.getSignature());
    }

    /**
     * <p>
     * 创建默认 Coco 请求签名验证器�?     * </p>
     * @return 请求签名验证�?     */
    @Bean
    @ConditionalOnMissingBean
    public CocoSignatureVerifier cocoSignatureVerifier() {
        return new HmacSha256CocoSignatureVerifier();
    }

    /**
     * <p>
     * 创建 Coco 请求签名过滤器注册器�?     * </p>
     * @param properties Coco Web 配置属�?     * @param secretResolver 请求签名密钥解析�?     * @param signatureVerifier 请求签名验证�?     * @param requestContextResolver Web 请求上下文解析器
     * @param requestCanonicalizer Web 请求规范化器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param requestMatcher Web 请求匹配�?     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @return 请求签名过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.signature", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoSignatureFilterRegistration")
    public FilterRegistrationBean<CocoSignatureFilter> cocoSignatureFilterRegistration(CocoWebProperties properties,
            CocoSignatureSecretResolver secretResolver, CocoSignatureVerifier signatureVerifier,
            CocoWebRequestContextResolver requestContextResolver, CocoWebRequestCanonicalizer requestCanonicalizer,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoWebRequestMatcher requestMatcher,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        FilterRegistrationBean<CocoSignatureFilter> registration = new FilterRegistrationBean<>(
                new CocoSignatureFilter(properties.getSignature(), secretResolver, signatureVerifier,
                        requestContextResolver, requestCanonicalizer, exceptionResponseWriter,
                        securityMetadataResolver, requestMatcher, null));
        registration.setName("cocoSignatureFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }
}
