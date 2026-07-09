package io.github.coco.feature.web;

import io.github.coco.feature.web.body.CocoRequestBodyResolver;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.encryption.AesGcmCocoRequestDecryptor;
import io.github.coco.feature.web.encryption.CocoEncryptionFilter;
import io.github.coco.feature.web.encryption.CocoEncryptionKeyResolver;
import io.github.coco.feature.web.encryption.CocoRequestDecryptor;
import io.github.coco.feature.web.encryption.PropertiesCocoEncryptionKeyResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Coco Web 加密自动配置�? * <p>
 * 注册加密密钥解析器、请求解密器和解密过滤器�? * </p>
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
public class CocoWebEncryptionAutoConfiguration {

    /**
     * <p>
     * 创建默认 Coco AES 解密密钥解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return AES 解密密钥解析�?     */
    @Bean
    @ConditionalOnMissingBean
    public CocoEncryptionKeyResolver cocoEncryptionKeyResolver(CocoWebProperties properties) {
        return new PropertiesCocoEncryptionKeyResolver(properties.getEncryption());
    }

    /**
     * <p>
     * 创建默认 Coco 请求解密器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return 请求解密�?     */
    @Bean
    @ConditionalOnMissingBean
    public CocoRequestDecryptor cocoRequestDecryptor(CocoWebProperties properties) {
        return new AesGcmCocoRequestDecryptor(properties.getEncryption());
    }

    /**
     * <p>
     * 创建 Coco 请求解密过滤器注册器�?     * </p>
     * @param properties Coco Web 配置属�?     * @param keyResolver AES 解密密钥解析�?     * @param requestDecryptor 请求解密�?     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param requestMatcher Web 请求匹配�?     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param requestBodyResolver 请求体解析器
     * @return 请求解密过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.encryption", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoEncryptionFilterRegistration")
    public FilterRegistrationBean<CocoEncryptionFilter> cocoEncryptionFilterRegistration(CocoWebProperties properties,
            CocoEncryptionKeyResolver keyResolver, CocoRequestDecryptor requestDecryptor,
            CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoWebRequestMatcher requestMatcher,
            CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoRequestBodyResolver requestBodyResolver) {
        FilterRegistrationBean<CocoEncryptionFilter> registration = new FilterRegistrationBean<>(
                new CocoEncryptionFilter(properties.getEncryption(), keyResolver, requestDecryptor,
                        requestContextResolver, exceptionResponseWriter, securityMetadataResolver, requestMatcher,
                        requestBodyResolver));
        registration.setName("cocoEncryptionFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
        return registration;
    }
}
