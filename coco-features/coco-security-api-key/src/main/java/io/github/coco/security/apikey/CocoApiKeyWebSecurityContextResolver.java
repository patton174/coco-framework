package io.github.coco.security.apikey;

import java.util.Enumeration;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 从单个请求头解析 API Key 安全上下文的适配器。
 * <p>
 * 一个请求仅在首次解析时读取和校验 Key；后续 ASYNC 或 ERROR 分派复用请求属性中的结果，
 * 仍由 Coco 核心 Web 安全过滤器负责每次分派的上下文绑定和清理。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoApiKeyWebSecurityContextResolver implements CocoWebSecurityContextResolver {

    private static final String RESULT_ATTRIBUTE = CocoApiKeyWebSecurityContextResolver.class.getName() + ".RESULT";

    private final CocoApiKeyProperties properties;

    private final CocoApiKeyVerifier verifier;

    /**
     * 创建 API Key Web 安全上下文解析器。
     * @param properties API Key 配置
     * @param verifier API Key 校验器
     */
    public CocoApiKeyWebSecurityContextResolver(CocoApiKeyProperties properties, CocoApiKeyVerifier verifier) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.verifier = Objects.requireNonNull(verifier, "verifier must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoSecurityContext> resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Object cached = request.getAttribute(RESULT_ATTRIBUTE);
        if (cached instanceof Resolution resolution) {
            return resolution.toContext();
        }
        Resolution resolution = resolveOnce(request);
        request.setAttribute(RESULT_ATTRIBUTE, resolution);
        return resolution.toContext();
    }

    private Resolution resolveOnce(HttpServletRequest request) {
        HeaderValue headerValue = singleHeaderValue(request, this.properties.getHeaderName());
        if (headerValue.headerMissing()) {
            return this.properties.isRequired() ? rejected() : Resolution.empty();
        }
        if (!headerValue.valid() || headerValue.value().isBlank()
                || headerValue.value().length() > this.properties.getMaxKeyLength()) {
            return rejected();
        }
        return this.verifier.verify(headerValue.value())
                .map(principal -> Resolution.authenticated(CocoSecurityContext.authenticated(principal)))
                .orElseGet(CocoApiKeyWebSecurityContextResolver::rejected);
    }

    private static HeaderValue singleHeaderValue(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null || !values.hasMoreElements()) {
            return HeaderValue.absent();
        }
        String value = values.nextElement();
        return values.hasMoreElements() ? HeaderValue.ambiguous() : HeaderValue.single(value);
    }

    private static Resolution rejected() {
        return Resolution.denied();
    }

    private record HeaderValue(boolean valid, boolean headerMissing, String value) {

        private static HeaderValue absent() {
            return new HeaderValue(true, true, null);
        }

        private static HeaderValue single(String value) {
            return new HeaderValue(true, false, value);
        }

        private static HeaderValue ambiguous() {
            return new HeaderValue(false, false, null);
        }
    }

    private record Resolution(Optional<CocoSecurityContext> context, boolean rejected) {

        private static Resolution authenticated(CocoSecurityContext context) {
            return new Resolution(Optional.of(context), false);
        }

        private static Resolution empty() {
            return new Resolution(Optional.empty(), false);
        }

        private static Resolution denied() {
            return new Resolution(Optional.empty(), true);
        }

        private Optional<CocoSecurityContext> toContext() {
            if (this.rejected) {
                throw new CocoApiKeyAuthenticationException();
            }
            return this.context;
        }
    }
}
