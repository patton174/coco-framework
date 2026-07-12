package io.github.coco.feature.web;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.web.body.CocoRequestBodyResolver;
import io.github.coco.feature.web.context.CocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.CocoClientIpResolver;
import io.github.coco.feature.web.context.CocoRequestCookieResolver;
import io.github.coco.feature.web.context.CocoRequestHeaderResolver;
import io.github.coco.feature.web.context.CocoRequestParameterResolver;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.DefaultCocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.DefaultCocoClientIpResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestCookieResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestHeaderResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestParameterResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.DefaultCocoWebRequestContextResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.feature.web.context.payload.CocoPayloadParameterResolver;
import io.github.coco.feature.web.context.payload.DefaultCocoPayloadParameterResolver;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolver;
import io.github.coco.feature.web.context.target.DefaultCocoWebRequestTargetResolver;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.request.metadata.DefaultCocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.request.metadata.DefaultCocoWebRequestSecurityMetadataResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco Web 请求上下文自动配置�? * <p>
 * 注册请求上下文解析、请求输入解析、请求安全元数据和请求匹配相关组件�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
public class CocoWebContextAutoConfiguration {

    /**
     * <p>
     * 创建默认 Coco 客户�?IP 解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return 客户�?IP 解析�?     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoClientIpResolver cocoClientIpResolver(CocoWebProperties properties) {
        return new DefaultCocoClientIpResolver(properties.getContext());
    }

    /**
     * <p>
     * 创建默认 Coco 浏览器指纹解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return 浏览器指纹解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoBrowserFingerprintResolver cocoBrowserFingerprintResolver(CocoWebProperties properties) {
        return new DefaultCocoBrowserFingerprintResolver(properties.getContext());
    }

    /**
     * <p>
     * 创建默认 Coco 请求头解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return 请求头解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoRequestHeaderResolver cocoRequestHeaderResolver(CocoWebProperties properties) {
        return new DefaultCocoRequestHeaderResolver(properties.getContext());
    }

    /**
     * <p>
     * 创建默认 Coco 请求 Cookie 解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return 请求 Cookie 解析�?     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoRequestCookieResolver cocoRequestCookieResolver(CocoWebProperties properties) {
        return new DefaultCocoRequestCookieResolver(properties.getContext());
    }

    /**
     * <p>
     * 创建默认 Coco 请求体参数解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @param objectMapper JSON 序列化器提供�?     * @return 请求体参数解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoPayloadParameterResolver cocoPayloadParameterResolver(CocoWebProperties properties,
            ObjectProvider<ObjectMapper> objectMapper) {
        String encryptedHeaderName = properties.getEncryption().getEncryptedHeaderName();
        return new DefaultCocoPayloadParameterResolver(properties.getContext().getParameter(),
                objectMapper.getIfAvailable(ObjectMapper::new),
                encryptedHeaderName == null ? Set.of() : Set.of(encryptedHeaderName));
    }

    /**
     * <p>
     * 创建默认 Coco 请求参数解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @param payloadParameterResolver 请求体参数解析器
     * @return 请求参数解析�?     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoRequestParameterResolver cocoRequestParameterResolver(CocoWebProperties properties,
            CocoPayloadParameterResolver payloadParameterResolver) {
        return new DefaultCocoRequestParameterResolver(properties.getContext().getParameter(),
                payloadParameterResolver);
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求目标解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return Web 请求目标解析�?     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebRequestTargetResolver cocoWebRequestTargetResolver(CocoWebProperties properties) {
        return new DefaultCocoWebRequestTargetResolver(properties.getContext());
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求安全输入解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @param requestHeaderResolver 请求头解析器
     * @param requestCookieResolver 请求 Cookie 解析�?     * @param requestParameterResolver 请求参数解析�?     * @param requestBodyResolver 请求体解析器
     * @return Web 请求安全输入解析�?     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebRequestSecurityInputResolver cocoWebRequestSecurityInputResolver(CocoWebProperties properties,
            CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestCookieResolver requestCookieResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoRequestBodyResolver requestBodyResolver) {
        return new DefaultCocoWebRequestSecurityInputResolver(properties.getContext(), requestHeaderResolver,
                requestCookieResolver, requestParameterResolver, properties.getSignature(), properties.getEncryption(),
                properties.getReplay(), requestBodyResolver);
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求安全元数据解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return Web 请求安全元数据解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebRequestSecurityMetadataResolver cocoWebRequestSecurityMetadataResolver(CocoWebProperties properties) {
        return new DefaultCocoWebRequestSecurityMetadataResolver(properties.getSignature(), properties.getEncryption(),
                properties.getReplay());
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求上下文解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @param clientIpResolver 客户�?IP 解析�?     * @param requestTargetResolver Web 请求目标解析�?     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestCookieResolver Cookie 解析�?     * @param requestParameterResolver 请求参数解析�?     * @param securityInputResolver Web 请求安全输入解析�?     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param requestBodyResolver 请求体解析器
     * @return Coco Web 请求上下文解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebRequestContextResolver cocoWebRequestContextResolver(CocoWebProperties properties,
            CocoClientIpResolver clientIpResolver,
            CocoWebRequestTargetResolver requestTargetResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestCookieResolver requestCookieResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoWebRequestSecurityInputResolver securityInputResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoRequestBodyResolver requestBodyResolver) {
        return new DefaultCocoWebRequestContextResolver(properties.getContext(),
                clientIpResolver, browserFingerprintResolver, requestHeaderResolver, requestCookieResolver,
                requestTargetResolver,
                requestParameterResolver, securityInputResolver, securityMetadataResolver, requestBodyResolver);
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求规范化器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return Coco Web 请求规范化器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoWebRequestCanonicalizer cocoWebRequestCanonicalizer(CocoWebProperties properties) {
        return new DefaultCocoWebRequestCanonicalizer(properties.getContext().getCanonicalization());
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求匹配器�?     * </p>
     * @return Coco Web 请求匹配�?     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebRequestMatcher cocoWebRequestMatcher() {
        return new DefaultCocoWebRequestMatcher();
    }
}
