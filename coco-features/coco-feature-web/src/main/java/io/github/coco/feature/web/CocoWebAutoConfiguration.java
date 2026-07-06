package io.github.coco.feature.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.common.CocoCommonProperties;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.i18n.api.CocoLocaleResolver;
import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.web.body.CocoRequestBodyCachingFilter;
import io.github.coco.feature.web.context.CocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.CocoClientIpResolver;
import io.github.coco.feature.web.context.CocoRequestHeaderResolver;
import io.github.coco.feature.web.context.CocoRequestParameterResolver;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.context.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.context.DefaultCocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.DefaultCocoClientIpResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestHeaderResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestParameterResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.DefaultCocoWebRequestContextResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.encryption.AesGcmCocoRequestDecryptor;
import io.github.coco.feature.web.encryption.CocoEncryptionFilter;
import io.github.coco.feature.web.encryption.CocoEncryptionKeyResolver;
import io.github.coco.feature.web.encryption.CocoRequestDecryptor;
import io.github.coco.feature.web.encryption.PropertiesCocoEncryptionKeyResolver;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.i18n.CocoWebLocaleResolver;
import io.github.coco.feature.web.replay.CocoReplayFilter;
import io.github.coco.feature.web.replay.CocoReplayKeyResolver;
import io.github.coco.feature.web.replay.CocoReplayStore;
import io.github.coco.feature.web.replay.DefaultCocoReplayKeyResolver;
import io.github.coco.feature.web.replay.InMemoryCocoReplayStore;
import io.github.coco.feature.web.response.CocoResponseBodyFactory;
import io.github.coco.feature.web.response.CocoResponseWrapAdvice;
import io.github.coco.feature.web.response.CocoSystemCodeProvider;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.feature.web.response.DefaultCocoResponseBodyFactory;
import io.github.coco.feature.web.signature.CocoSignatureFilter;
import io.github.coco.feature.web.signature.CocoSignatureSecretResolver;
import io.github.coco.feature.web.signature.CocoSignatureVerifier;
import io.github.coco.feature.web.signature.HmacSha256CocoSignatureVerifier;
import io.github.coco.feature.web.signature.PropertiesCocoSignatureSecretResolver;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Coco Web 功能自动配置。
 * <p>
 * 负责为 Web 功能模块注册国际化消息资源、统一响应、统一异常、Trace、访问日志、请求上下文、签名、加密和防重放相关基础设施。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(before = CocoCommonAutoConfiguration.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@EnableConfigurationProperties({ CocoWebProperties.class, CocoCommonProperties.class })
public class CocoWebAutoConfiguration {

    /**
     * <p>
     * 创建 Servlet Web 请求语言解析器。
     * </p>
     * @param properties Coco 通用配置属性
     * @return Coco Web 请求语言解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoLocaleResolver cocoWebLocaleResolver(CocoCommonProperties properties) {
        return new CocoWebLocaleResolver(properties.getI18n());
    }

    /**
     * <p>
     * 注册 Web 功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoWebMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoWebMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-web-messages");
    }

    /**
     * <p>
     * 创建默认 Coco 异常 HTTP 状态解析器。
     * </p>
     * @return 异常 HTTP 状态解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoExceptionHttpStatusResolver cocoExceptionHttpStatusResolver() {
        return new DefaultCocoExceptionHttpStatusResolver();
    }

    /**
     * <p>
     * 创建默认系统响应码提供器。
     * </p>
     * @return 系统响应码提供器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoSystemCodeProvider cocoSystemCodeProvider() {
        return CocoSystemCodes.defaults();
    }

    /**
     * <p>
     * 创建默认 Coco 响应体工厂。
     * </p>
     * @return Coco 响应体工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoResponseBodyFactory cocoResponseBodyFactory() {
        return new DefaultCocoResponseBodyFactory();
    }

    /**
     * <p>
     * 创建默认 Coco 客户端 IP 解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return 客户端 IP 解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoClientIpResolver cocoClientIpResolver(CocoWebProperties properties) {
        return new DefaultCocoClientIpResolver(properties.getContext());
    }

    /**
     * <p>
     * 创建默认 Coco 浏览器指纹解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return 浏览器指纹解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoBrowserFingerprintResolver cocoBrowserFingerprintResolver(CocoWebProperties properties) {
        return new DefaultCocoBrowserFingerprintResolver(properties.getContext());
    }

    /**
     * <p>
     * 创建默认 Coco 请求头解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return 请求头解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoRequestHeaderResolver cocoRequestHeaderResolver(CocoWebProperties properties) {
        return new DefaultCocoRequestHeaderResolver(properties.getContext());
    }

    /**
     * <p>
     * 创建默认 Coco 请求参数解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return 请求参数解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoRequestParameterResolver cocoRequestParameterResolver(CocoWebProperties properties) {
        return new DefaultCocoRequestParameterResolver(properties.getContext().getParameter());
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求安全输入解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     * @return Web 请求安全输入解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebRequestSecurityInputResolver cocoWebRequestSecurityInputResolver(CocoWebProperties properties,
            CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestParameterResolver requestParameterResolver) {
        return new DefaultCocoWebRequestSecurityInputResolver(properties.getContext(), requestHeaderResolver,
                requestParameterResolver, properties.getSignature(), properties.getEncryption(),
                properties.getReplay());
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求安全元数据解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return Web 请求安全元数据解析器
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
     * 创建默认 Coco Web 请求上下文解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param clientIpResolver 客户端 IP 解析器
     * @param browserFingerprintResolver 浏览器指纹解析器
     * @param requestHeaderResolver 请求头解析器
     * @param requestParameterResolver 请求参数解析器
     * @param securityInputResolver Web 请求安全输入解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @return Coco Web 请求上下文解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebRequestContextResolver cocoWebRequestContextResolver(CocoWebProperties properties,
            CocoClientIpResolver clientIpResolver,
            CocoBrowserFingerprintResolver browserFingerprintResolver, CocoRequestHeaderResolver requestHeaderResolver,
            CocoRequestParameterResolver requestParameterResolver,
            CocoWebRequestSecurityInputResolver securityInputResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver) {
        return new DefaultCocoWebRequestContextResolver(properties.getContext(), properties.getAccessLog(),
                clientIpResolver, browserFingerprintResolver, requestHeaderResolver, requestParameterResolver,
                securityInputResolver, securityMetadataResolver);
    }

    /**
     * <p>
     * 创建默认 Coco Web 请求规范化器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return Coco Web 请求规范化器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoWebRequestCanonicalizer cocoWebRequestCanonicalizer(CocoWebProperties properties) {
        return new DefaultCocoWebRequestCanonicalizer(properties.getContext().getCanonicalization());
    }

    /**
     * <p>
     * 创建默认 Coco 请求签名密钥解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return 请求签名密钥解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoSignatureSecretResolver cocoSignatureSecretResolver(CocoWebProperties properties) {
        return new PropertiesCocoSignatureSecretResolver(properties.getSignature());
    }

    /**
     * <p>
     * 创建默认 Coco 请求签名验证器。
     * </p>
     * @return 请求签名验证器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoSignatureVerifier cocoSignatureVerifier() {
        return new HmacSha256CocoSignatureVerifier();
    }

    /**
     * <p>
     * 创建默认 Coco AES 解密密钥解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return AES 解密密钥解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoEncryptionKeyResolver cocoEncryptionKeyResolver(CocoWebProperties properties) {
        return new PropertiesCocoEncryptionKeyResolver(properties.getEncryption());
    }

    /**
     * <p>
     * 创建默认 Coco 请求解密器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return 请求解密器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoRequestDecryptor cocoRequestDecryptor(CocoWebProperties properties) {
        return new AesGcmCocoRequestDecryptor(properties.getEncryption());
    }

    /**
     * <p>
     * 创建默认 Coco 防重放键解析器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return 防重放键解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoReplayKeyResolver cocoReplayKeyResolver(CocoWebProperties properties) {
        return new DefaultCocoReplayKeyResolver(properties.getReplay());
    }

    /**
     * <p>
     * 创建默认 Coco 防重放存储。
     * </p>
     * @param properties Coco Web 配置属性
     * @return 防重放存储
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoReplayStore cocoReplayStore(CocoWebProperties properties) {
        return new InMemoryCocoReplayStore(properties.getReplay());
    }

    /**
     * <p>
     * 创建 Coco Web 全局异常处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param httpStatusResolver 异常 HTTP 状态解析器
     * @param codeProvider 系统响应码提供器
     * @return Coco Web 全局异常处理器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebExceptionHandler cocoWebExceptionHandler(CocoMessageService messageService,
            CocoExceptionHttpStatusResolver httpStatusResolver, CocoSystemCodeProvider codeProvider,
            CocoWebProperties properties, CocoResponseBodyFactory responseBodyFactory) {
        return new CocoWebExceptionHandler(messageService, httpStatusResolver, codeProvider,
                properties.getResponse(), responseBodyFactory);
    }

    /**
     * <p>
     * 创建 Coco 过滤器异常响应写出器。
     * </p>
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器提供器
     * @return 过滤器异常响应写出器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoFilterExceptionResponseWriter cocoFilterExceptionResponseWriter(
            CocoWebExceptionHandler exceptionHandler, ObjectProvider<ObjectMapper> objectMapper) {
        return new CocoFilterExceptionResponseWriter(exceptionHandler, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * <p>
     * 创建 Coco Web 正常响应包装处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param properties Coco Web 配置属性
     * @param codeProvider 系统响应码提供器
     * @param objectMapper JSON 序列化器提供器
     * @return Coco Web 正常响应包装处理器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.response-wrap", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public CocoResponseWrapAdvice cocoResponseWrapAdvice(CocoMessageService messageService,
            CocoWebProperties properties, CocoSystemCodeProvider codeProvider, ObjectProvider<ObjectMapper> objectMapper,
            CocoResponseBodyFactory responseBodyFactory) {
        return new CocoResponseWrapAdvice(messageService, properties.getResponseWrap(),
                codeProvider, objectMapper.getIfAvailable(ObjectMapper::new), properties.getResponse(),
                responseBodyFactory);
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @return 请求体缓存过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.request-body", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoRequestBodyCachingFilterRegistration")
    public FilterRegistrationBean<CocoRequestBodyCachingFilter> cocoRequestBodyCachingFilterRegistration(
            CocoWebProperties properties, CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        FilterRegistrationBean<CocoRequestBodyCachingFilter> registration = new FilterRegistrationBean<>(
                new CocoRequestBodyCachingFilter(properties.getRequestBody(), properties.getSignature(),
                        properties.getEncryption(), exceptionResponseWriter));
        registration.setName("cocoRequestBodyCachingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * <p>
     * 创建 Coco Trace 过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return Trace 过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoTraceFilterRegistration")
    public FilterRegistrationBean<CocoTraceFilter> cocoTraceFilterRegistration(CocoWebProperties properties,
            ObjectProvider<CocoAccessLogRecorder> accessLogRecorders,
            CocoWebRequestContextResolver requestContextResolver) {
        FilterRegistrationBean<CocoTraceFilter> registration = new FilterRegistrationBean<>(
                new CocoTraceFilter(properties.getTrace(), accessLogRecorders.orderedStream().toList(),
                        properties.getAccessLog(), requestContextResolver));
        registration.setName("cocoTraceFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /**
     * <p>
     * 创建 Coco 请求签名过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param secretResolver 请求签名密钥解析器
     * @param signatureVerifier 请求签名验证器
     * @param requestContextResolver Web 请求上下文解析器
     * @param requestCanonicalizer Web 请求规范化器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
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
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        FilterRegistrationBean<CocoSignatureFilter> registration = new FilterRegistrationBean<>(
                new CocoSignatureFilter(properties.getSignature(), secretResolver, signatureVerifier,
                        requestContextResolver, requestCanonicalizer, exceptionResponseWriter,
                        securityMetadataResolver));
        registration.setName("cocoSignatureFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    /**
     * <p>
     * 创建 Coco Web 防重放过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param replayStore 防重放存储
     * @param replayKeyResolver 防重放键解析器
     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @return 防重放过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.replay", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoReplayFilterRegistration")
    public FilterRegistrationBean<CocoReplayFilter> cocoReplayFilterRegistration(CocoWebProperties properties,
            CocoReplayStore replayStore, CocoReplayKeyResolver replayKeyResolver,
            CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        FilterRegistrationBean<CocoReplayFilter> registration = new FilterRegistrationBean<>(
                new CocoReplayFilter(properties.getReplay(), replayStore, replayKeyResolver, requestContextResolver,
                        securityMetadataResolver, exceptionResponseWriter));
        registration.setName("cocoReplayFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
        return registration;
    }

    /**
     * <p>
     * 创建 Coco 请求解密过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param keyResolver AES 解密密钥解析器
     * @param requestDecryptor 请求解密器
     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param exceptionResponseWriter 过滤器异常响应写出器
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
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        FilterRegistrationBean<CocoEncryptionFilter> registration = new FilterRegistrationBean<>(
                new CocoEncryptionFilter(properties.getEncryption(), keyResolver, requestDecryptor,
                        requestContextResolver, exceptionResponseWriter, securityMetadataResolver));
        registration.setName("cocoEncryptionFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registration;
    }
}
