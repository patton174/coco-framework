package io.github.coco.security.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.CocoCommonProperties;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.web.CocoWebProperties;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.response.DefaultCocoResponseBodyFactory;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.i18n.CocoMessageService;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CocoApiKeyAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        CocoSecurityContextHolder.clear();
    }

    @Test
    void writesUnifiedUnauthorizedResponseWithoutLeakingKeyOrDigestAndRestoresContext() throws Exception {
        CocoApiKeyProperties properties = CocoApiKeyWebSecurityContextResolverTest.enabledProperties();
        CocoApiKeyWebSecurityContextResolver resolver = new CocoApiKeyWebSecurityContextResolver(properties,
                new DefaultCocoApiKeyVerifier(properties.getCredentials()));
        CocoSecurityWebFilter securityFilter = new CocoSecurityWebFilter(resolver);
        CocoApiKeyAuthenticationFilter filter = new CocoApiKeyAuthenticationFilter(responseWriter());
        CocoSecurityContext previous = CocoSecurityContext.authenticated(CocoSecurityPrincipal.of("previous", "Previous"));
        CocoSecurityContextHolder.set(previous);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setDispatcherType(DispatcherType.ERROR);
        request.addHeader("X-API-Key", "not-the-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                securityFilter.doFilter(servletRequest, servletResponse, (ignoredRequest, ignoredResponse) -> {
                    throw new AssertionError("business chain must not execute");
                }));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("not-the-key")
                .doesNotContain("074c1fd1ac9d1c67ec22e8ae841db4c570a2740372e70b0bc3c763416cac9ca0");
        assertThat(CocoSecurityContextHolder.current()).contains(previous);
    }

    private static CocoFilterExceptionResponseWriter responseWriter() {
        CocoMessageService messageService = new CocoMessageService() {
            @Override
            public String getMessage(String code, Object... args) {
                return code;
            }

            @Override
            public String getMessage(String code, java.util.Locale locale, Object... args) {
                return code;
            }

            @Override
            public String getMessageOrDefault(String code, String defaultMessage, Object... args) {
                return defaultMessage;
            }

            @Override
            public String getMessageOrDefault(String code, String defaultMessage, java.util.Locale locale,
                    Object... args) {
                return defaultMessage;
            }

            @Override
            public String resolve(io.github.coco.i18n.CocoMessage message) {
                return message.defaultMessage();
            }

            @Override
            public String resolve(io.github.coco.i18n.CocoMessage message, java.util.Locale locale) {
                return message.defaultMessage();
            }
        };
        CocoWebExceptionHandler handler = new CocoWebExceptionHandler(messageService,
                new DefaultCocoExceptionHttpStatusResolver(), CocoSystemCodes.defaults(), new CocoWebProperties().getResponse(),
                new CocoWebProperties().getTrace(), new DefaultCocoResponseBodyFactory(), null,
                new CocoCommonProperties().getI18n().getDefaultLocale());
        return new CocoFilterExceptionResponseWriter(handler, new ObjectMapper());
    }
}
