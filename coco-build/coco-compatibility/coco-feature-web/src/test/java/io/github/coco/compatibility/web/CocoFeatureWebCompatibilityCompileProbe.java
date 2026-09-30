package io.github.coco.compatibility.web;

import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.body.CocoRequestBodyCachingFilter;
import io.github.coco.feature.web.body.CocoRequestBodyCachingMode;
import io.github.coco.feature.web.body.CocoRequestBodyMetadata;
import io.github.coco.feature.web.body.CocoRequestBodyProperties;
import io.github.coco.feature.web.body.CocoRequestBodyResolver;
import io.github.coco.feature.web.body.CocoRequestBodyStage;
import io.github.coco.feature.web.body.CocoResolvedRequestBody;
import io.github.coco.feature.web.body.DefaultCocoRequestBodyResolver;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import io.github.coco.feature.web.CocoWebContextAutoConfiguration;
import io.github.coco.feature.web.CocoWebEncryptionAutoConfiguration;
import io.github.coco.feature.web.CocoWebExceptionAutoConfiguration;
import io.github.coco.feature.web.CocoWebFeature;
import io.github.coco.feature.web.CocoWebI18nAutoConfiguration;
import io.github.coco.feature.web.CocoWebJdbcReplayAutoConfiguration;
import io.github.coco.feature.web.CocoWebProperties;
import io.github.coco.feature.web.CocoWebRedisReplayAutoConfiguration;
import io.github.coco.feature.web.CocoWebReplayAutoConfiguration;
import io.github.coco.feature.web.CocoWebRequestBodyAutoConfiguration;
import io.github.coco.feature.web.CocoWebResponseAutoConfiguration;
import io.github.coco.feature.web.CocoWebSignatureAutoConfiguration;
import io.github.coco.feature.web.CocoWebTraceAutoConfiguration;
import io.github.coco.feature.web.context.CocoBrowserFingerprint;
import io.github.coco.feature.web.context.CocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.CocoClientIpResolution;
import io.github.coco.feature.web.context.CocoClientIpResolver;
import io.github.coco.feature.web.context.CocoClientIpSource;
import io.github.coco.feature.web.context.CocoIpAddressSupport;
import io.github.coco.feature.web.context.CocoRequestCookieResolver;
import io.github.coco.feature.web.context.CocoRequestHeaderResolver;
import io.github.coco.feature.web.context.CocoRequestParameterResolver;
import io.github.coco.feature.web.context.CocoSensitiveRequestHeaderContributor;
import io.github.coco.feature.web.context.CocoWebContextProperties;
import io.github.coco.feature.web.context.CocoWebParameterProperties;
import io.github.coco.feature.web.context.CocoWebParameterSource;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationProperties;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationPurpose;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextPhase;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.context.CocoWebRequestMatcherProperties;
import io.github.coco.feature.web.context.CocoWebRequestMatchRule;
import io.github.coco.feature.web.context.CocoWebRequestParameters;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.CocoWebRequestSnapshotAttributes;
import io.github.coco.feature.web.context.DefaultCocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.DefaultCocoClientIpResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestCookieResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestHeaderResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestParameterResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.DefaultCocoWebRequestContextResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.feature.web.context.payload.CocoPayloadParameterProperties;
import io.github.coco.feature.web.context.payload.CocoPayloadParameterResolver;
import io.github.coco.feature.web.context.payload.CocoWebPayloadParseResult;
import io.github.coco.feature.web.context.payload.CocoWebPayloadParseStatus;
import io.github.coco.feature.web.context.payload.DefaultCocoPayloadParameterResolver;
import io.github.coco.feature.web.context.target.CocoWebRequestTarget;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetProperties;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolution;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolver;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetSource;
import io.github.coco.feature.web.context.target.DefaultCocoWebRequestTargetResolver;
import io.github.coco.feature.web.cors.CocoCorsAutoConfiguration;
import io.github.coco.feature.web.cors.CocoCorsProperties;
import io.github.coco.feature.web.encryption.AesGcmCocoRequestDecryptor;
import io.github.coco.feature.web.encryption.CocoCryptoTextEncoding;
import io.github.coco.feature.web.encryption.CocoEncryptedRequest;
import io.github.coco.feature.web.encryption.CocoEncryptionAssociatedData;
import io.github.coco.feature.web.encryption.CocoEncryptionFilter;
import io.github.coco.feature.web.encryption.CocoEncryptionKey;
import io.github.coco.feature.web.encryption.CocoEncryptionKeyResolver;
import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.encryption.CocoRequestDecryptException;
import io.github.coco.feature.web.encryption.CocoRequestDecryptionContext;
import io.github.coco.feature.web.encryption.CocoRequestDecryptor;
import io.github.coco.feature.web.encryption.PropertiesCocoEncryptionKeyResolver;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoFieldError;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoPayloadTooLargeException;
import io.github.coco.feature.web.exception.CocoWebErrorResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.headers.CocoSecurityHeadersAutoConfiguration;
import io.github.coco.feature.web.headers.CocoSecurityHeadersFilter;
import io.github.coco.feature.web.headers.CocoSecurityHeadersProperties;
import io.github.coco.feature.web.i18n.CocoWebLocaleResolver;
import io.github.coco.feature.web.page.CocoPageAutoConfiguration;
import io.github.coco.feature.web.page.CocoPageInterceptor;
import io.github.coco.feature.web.page.CocoPageProperties;
import io.github.coco.feature.web.replay.CocoReplayFilter;
import io.github.coco.feature.web.replay.CocoReplayKey;
import io.github.coco.feature.web.replay.CocoReplayKeyResolver;
import io.github.coco.feature.web.replay.CocoReplayProperties;
import io.github.coco.feature.web.replay.CocoReplayRequestShapeFilter;
import io.github.coco.feature.web.replay.CocoReplayStore;
import io.github.coco.feature.web.replay.CocoReplayStoreType;
import io.github.coco.feature.web.replay.DefaultCocoReplayKeyResolver;
import io.github.coco.feature.web.replay.InMemoryCocoReplayStore;
import io.github.coco.feature.web.replay.JdbcCocoReplayStore;
import io.github.coco.feature.web.replay.RedisCocoReplayStore;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.request.metadata.CocoWebSecurityMetadataSource;
import io.github.coco.feature.web.request.metadata.DefaultCocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.request.metadata.DefaultCocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.response.CocoApiResponse;
import io.github.coco.feature.web.response.CocoIgnoreResponseWrap;
import io.github.coco.feature.web.response.CocoResponseBodyFactory;
import io.github.coco.feature.web.response.CocoResponseMetadata;
import io.github.coco.feature.web.response.CocoResponseMetadataMode;
import io.github.coco.feature.web.response.CocoResponsePayload;
import io.github.coco.feature.web.response.CocoResponseProperties;
import io.github.coco.feature.web.response.CocoResponseWrapAdvice;
import io.github.coco.feature.web.response.CocoResponseWrapProperties;
import io.github.coco.feature.web.response.CocoSystemCodeProvider;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.feature.web.response.DefaultCocoResponseBodyFactory;
import io.github.coco.feature.web.signature.CocoSignatureFilter;
import io.github.coco.feature.web.signature.CocoSignatureProperties;
import io.github.coco.feature.web.signature.CocoSignatureRequest;
import io.github.coco.feature.web.signature.CocoSignatureSecret;
import io.github.coco.feature.web.signature.CocoSignatureSecretResolver;
import io.github.coco.feature.web.signature.CocoSignatureVerificationContext;
import io.github.coco.feature.web.signature.CocoSignatureVerifier;
import io.github.coco.feature.web.signature.HmacSha256CocoSignatureVerifier;
import io.github.coco.feature.web.signature.PropertiesCocoSignatureSecretResolver;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import io.github.coco.feature.web.trace.CocoTraceIdValidator;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import io.github.coco.feature.web.trace.DefaultCocoTraceIdValidator;

/**
 * {@code coco-feature-web} 兼容门面编译探针。
 * <p>
 * 以编译期引用的方式钉住旧坐标 {@code coco-feature-web} 曾经对外暴露、现由 {@code coco-web} 承载的公开类型。
 * 只要业务项目仍通过旧坐标引入依赖，这些类型就必须继续可解析；任何一个类型被移动、改名或降低可见性，
 * 本模块的测试编译都会失败，从而在发布前暴露兼容面的破坏。该类不含测试方法，运行期也不会被加载。
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
 * @since 3.0.0
 */
final class CocoFeatureWebCompatibilityCompileProbe {

    /** 旧坐标消费者可能直接引用的公开类型；仅用于编译期校验，不会被读取。 */
    private static final Class<?>[] PUBLIC_TYPES = {
            CocoAccessLogCaptureProperties.class,
            CocoCachedBodyHttpServletRequest.class,
            CocoCachedRequestBody.class,
            CocoRequestBodyCachingFilter.class,
            CocoRequestBodyCachingMode.class,
            CocoRequestBodyMetadata.class,
            CocoRequestBodyProperties.class,
            CocoRequestBodyResolver.class,
            CocoRequestBodyStage.class,
            CocoResolvedRequestBody.class,
            DefaultCocoRequestBodyResolver.class,
            CocoWebAutoConfiguration.class,
            CocoWebContextAutoConfiguration.class,
            CocoWebEncryptionAutoConfiguration.class,
            CocoWebExceptionAutoConfiguration.class,
            CocoWebFeature.class,
            CocoWebI18nAutoConfiguration.class,
            CocoWebJdbcReplayAutoConfiguration.class,
            CocoWebProperties.class,
            CocoWebRedisReplayAutoConfiguration.class,
            CocoWebReplayAutoConfiguration.class,
            CocoWebRequestBodyAutoConfiguration.class,
            CocoWebResponseAutoConfiguration.class,
            CocoWebSignatureAutoConfiguration.class,
            CocoWebTraceAutoConfiguration.class,
            CocoBrowserFingerprint.class,
            CocoBrowserFingerprintResolver.class,
            CocoClientIpResolution.class,
            CocoClientIpResolver.class,
            CocoClientIpSource.class,
            CocoIpAddressSupport.class,
            CocoRequestCookieResolver.class,
            CocoRequestHeaderResolver.class,
            CocoRequestParameterResolver.class,
            CocoSensitiveRequestHeaderContributor.class,
            CocoWebContextProperties.class,
            CocoWebParameterProperties.class,
            CocoWebParameterSource.class,
            CocoWebRequestCanonicalForm.class,
            CocoWebRequestCanonicalizationContext.class,
            CocoWebRequestCanonicalizationProperties.class,
            CocoWebRequestCanonicalizationPurpose.class,
            CocoWebRequestCanonicalizer.class,
            CocoWebRequestContextPhase.class,
            CocoWebRequestContextResolver.class,
            CocoWebRequestMatcher.class,
            CocoWebRequestMatcherProperties.class,
            CocoWebRequestMatchRule.class,
            CocoWebRequestParameters.class,
            CocoWebRequestSnapshot.class,
            CocoWebRequestSnapshotAttributes.class,
            DefaultCocoBrowserFingerprintResolver.class,
            DefaultCocoClientIpResolver.class,
            DefaultCocoRequestCookieResolver.class,
            DefaultCocoRequestHeaderResolver.class,
            DefaultCocoRequestParameterResolver.class,
            DefaultCocoWebRequestCanonicalizer.class,
            DefaultCocoWebRequestContextResolver.class,
            DefaultCocoWebRequestMatcher.class,
            CocoPayloadParameterProperties.class,
            CocoPayloadParameterResolver.class,
            CocoWebPayloadParseResult.class,
            CocoWebPayloadParseStatus.class,
            DefaultCocoPayloadParameterResolver.class,
            CocoWebRequestTarget.class,
            CocoWebRequestTargetProperties.class,
            CocoWebRequestTargetResolution.class,
            CocoWebRequestTargetResolver.class,
            CocoWebRequestTargetSource.class,
            DefaultCocoWebRequestTargetResolver.class,
            CocoCorsAutoConfiguration.class,
            CocoCorsProperties.class,
            AesGcmCocoRequestDecryptor.class,
            CocoCryptoTextEncoding.class,
            CocoEncryptedRequest.class,
            CocoEncryptionAssociatedData.class,
            CocoEncryptionFilter.class,
            CocoEncryptionKey.class,
            CocoEncryptionKeyResolver.class,
            CocoEncryptionProperties.class,
            CocoRequestDecryptException.class,
            CocoRequestDecryptionContext.class,
            CocoRequestDecryptor.class,
            PropertiesCocoEncryptionKeyResolver.class,
            CocoExceptionHttpStatusResolver.class,
            CocoFieldError.class,
            CocoFilterExceptionResponseWriter.class,
            CocoPayloadTooLargeException.class,
            CocoWebErrorResponseWriter.class,
            CocoWebExceptionHandler.class,
            DefaultCocoExceptionHttpStatusResolver.class,
            CocoSecurityHeadersAutoConfiguration.class,
            CocoSecurityHeadersFilter.class,
            CocoSecurityHeadersProperties.class,
            CocoWebLocaleResolver.class,
            CocoPageAutoConfiguration.class,
            CocoPageInterceptor.class,
            CocoPageProperties.class,
            CocoReplayFilter.class,
            CocoReplayKey.class,
            CocoReplayKeyResolver.class,
            CocoReplayProperties.class,
            CocoReplayRequestShapeFilter.class,
            CocoReplayStore.class,
            CocoReplayStoreType.class,
            DefaultCocoReplayKeyResolver.class,
            InMemoryCocoReplayStore.class,
            JdbcCocoReplayStore.class,
            RedisCocoReplayStore.class,
            CocoWebRequestSecurityInput.class,
            CocoWebRequestSecurityInputResolver.class,
            CocoWebRequestSecurityMetadata.class,
            CocoWebRequestSecurityMetadataResolver.class,
            CocoWebSecurityMetadataSource.class,
            DefaultCocoWebRequestSecurityInputResolver.class,
            DefaultCocoWebRequestSecurityMetadataResolver.class,
            CocoApiResponse.class,
            CocoIgnoreResponseWrap.class,
            CocoResponseBodyFactory.class,
            CocoResponseMetadata.class,
            CocoResponseMetadataMode.class,
            CocoResponsePayload.class,
            CocoResponseProperties.class,
            CocoResponseWrapAdvice.class,
            CocoResponseWrapProperties.class,
            CocoSystemCodeProvider.class,
            CocoSystemCodes.class,
            DefaultCocoResponseBodyFactory.class,
            CocoSignatureFilter.class,
            CocoSignatureProperties.class,
            CocoSignatureRequest.class,
            CocoSignatureSecret.class,
            CocoSignatureSecretResolver.class,
            CocoSignatureVerificationContext.class,
            CocoSignatureVerifier.class,
            HmacSha256CocoSignatureVerifier.class,
            PropertiesCocoSignatureSecretResolver.class,
            CocoTraceFilter.class,
            CocoTraceIdValidator.class,
            CocoTraceProperties.class,
            DefaultCocoTraceIdValidator.class
    };

    private CocoFeatureWebCompatibilityCompileProbe() {
    }
}
