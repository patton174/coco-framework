package io.github.coco.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

class CocoSecurityJwtAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class,
                    CocoSecurityJwtAutoConfiguration.class,
                    CocoSecurityAutoConfiguration.class))
            .withUserConfiguration(SecurityInfrastructure.class)
            .withPropertyValues(
                    "coco.common.i18n.basename=coco-messages",
                    "coco.security.jwt.enabled=true",
                    "coco.security.jwt.issuer-uri=https://idp.example.com/issuer",
                    "coco.security.jwt.jwk-set-uri=https://idp.example.com/issuer/jwks",
                    "coco.security.jwt.audiences=orders-api");

    @Test
    void registersStrictOptInAdapterAndFailClosedFallbackChain() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JwtDecoder.class);
            assertThat(context).hasSingleBean(CocoJwtAuthoritiesConverter.class);
            assertThat(context).hasSingleBean(CocoJwtPrincipalConverter.class);
            assertThat(context).hasSingleBean(CocoJwtResourceServerConfigurer.class);
            assertThat(context).hasBean("cocoJwtAuthenticationEntryPoint");
            assertThat(context).hasBean("cocoJwtAccessDeniedHandler");
            assertThat(context).hasBean("cocoJwtSecurityFilterChain");
            SecurityFilterChain chain = context.getBean("cocoJwtSecurityFilterChain", SecurityFilterChain.class);
            assertThat(chain.getFilters()).anyMatch(BearerTokenAuthenticationFilter.class::isInstance);
            assertThat(chain.getFilters()).anyMatch(AuthorizationFilter.class::isInstance);
            assertThat(chain.getFilters()).anyMatch(CocoSecurityWebFilter.class::isInstance);
            int bearerIndex = indexOf(chain, BearerTokenAuthenticationFilter.class);
            int cocoIndex = indexOf(chain, CocoSecurityWebFilter.class);
            assertThat(cocoIndex).isGreaterThan(bearerIndex);
        });
    }

    @Test
    void backsOffFallbackChainWhenBusinessProvidesSecurityFilterChain() {
        SecurityFilterChain businessChain = new DefaultSecurityFilterChain(request -> true, List.of());

        this.contextRunner
                .withBean("businessSecurityFilterChain", SecurityFilterChain.class, () -> businessChain)
                .run(context -> {
                    assertThat(context).doesNotHaveBean("cocoJwtSecurityFilterChain");
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context.getBean(SecurityFilterChain.class)).isSameAs(businessChain);
                    assertThat(context).hasSingleBean(CocoJwtResourceServerConfigurer.class);
                });
    }

    @Test
    void userConvertersOverrideDefaults() {
        CocoJwtAuthoritiesConverter authoritiesConverter = jwt ->
                List.of(new SimpleGrantedAuthority("CUSTOM"));
        CocoJwtPrincipalConverter principalConverter = (jwt, authorities) ->
                CocoSecurityPrincipal.of("custom", "Custom");

        this.contextRunner
                .withBean(CocoJwtAuthoritiesConverter.class, () -> authoritiesConverter)
                .withBean(CocoJwtPrincipalConverter.class, () -> principalConverter)
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoJwtAuthoritiesConverter.class);
                    assertThat(context).hasSingleBean(CocoJwtPrincipalConverter.class);
                    assertThat(context.getBean(CocoJwtAuthoritiesConverter.class)).isSameAs(authoritiesConverter);
                    assertThat(context.getBean(CocoJwtPrincipalConverter.class)).isSameAs(principalConverter);
                    assertThat(context.getBean(CocoJwtAuthoritiesConverter.class).convert(jwt()))
                            .extracting(authority -> authority.getAuthority())
                            .containsExactly("CUSTOM");
                });
    }

    @Test
    void userJwtDecoderAndAuthenticationConverterOverrideFallbackDefaults() {
        JwtDecoder decoder = token -> jwt();
        Converter<Jwt, AbstractAuthenticationToken> authenticationConverter = token ->
                new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("CUSTOM")));

        this.contextRunner
                .withBean(JwtDecoder.class, () -> decoder)
                .withBean("cocoJwtAuthenticationConverter", Converter.class, () -> authenticationConverter)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context.getBean(JwtDecoder.class)).isSameAs(decoder);
                    assertThat(context.getBean("cocoJwtAuthenticationConverter")).isSameAs(authenticationConverter);
                    assertThat(context).hasBean("cocoJwtSecurityFilterChain");
                });
    }

    @Test
    void customPrincipalConverterIsUsedByJwtBridge() {
        CocoJwtPrincipalConverter principalConverter = (jwt, authorities) ->
                CocoSecurityPrincipal.of("custom", "Custom");

        this.contextRunner
                .withBean(CocoJwtPrincipalConverter.class, () -> principalConverter)
                .run(context -> {
                    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(),
                            List.of(new SimpleGrantedAuthority("SCOPE_order:read"))));
                    try {
                        CocoWebSecurityContextResolver resolver = context.getBean(CocoWebSecurityContextResolver.class);

                        assertThat(resolver.resolve(new MockHttpServletRequest("GET", "/orders")))
                                .get()
                                .extracting(securityContext -> securityContext.principal().principalId())
                                .isEqualTo("custom");
                    }
                    finally {
                        SecurityContextHolder.clearContext();
                    }
                });
    }

    @Test
    void userNamedFailureHandlersOverrideDefaults() {
        AuthenticationEntryPoint entryPoint = (request, response, exception) -> response.setStatus(499);
        AccessDeniedHandler deniedHandler = (request, response, exception) -> response.setStatus(498);

        this.contextRunner
                .withBean("cocoJwtAuthenticationEntryPoint", AuthenticationEntryPoint.class, () -> entryPoint)
                .withBean("cocoJwtAccessDeniedHandler", AccessDeniedHandler.class, () -> deniedHandler)
                .run(context -> {
                    assertThat(context.getBean("cocoJwtAuthenticationEntryPoint")).isSameAs(entryPoint);
                    assertThat(context.getBean("cocoJwtAccessDeniedHandler")).isSameAs(deniedHandler);
                    assertThat(context).hasSingleBean(CocoJwtResourceServerConfigurer.class);
                });
    }

    @Test
    void bearerFailuresUseCocoEnvelopeAndRetainAuthenticateHeader() {
        this.contextRunner.run(context -> {
            AuthenticationEntryPoint entryPoint = context.getBean("cocoJwtAuthenticationEntryPoint",
                    AuthenticationEntryPoint.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request, response, new InvalidBearerTokenException("invalid token"));

            Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
            assertThat(body.get("success")).isEqualTo(false);
            assertThat(body.get("code")).isEqualTo(401);
            assertThat(body.get("message")).isEqualTo("Bearer token authentication failed.");
        });
    }

    @Test
    void accessDeniedUsesCocoEnvelope() {
        this.contextRunner.run(context -> {
            AccessDeniedHandler deniedHandler = context.getBean("cocoJwtAccessDeniedHandler",
                    AccessDeniedHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");
            MockHttpServletResponse response = new MockHttpServletResponse();

            deniedHandler.handle(request, response, new AccessDeniedException("denied"));

            Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(body.get("success")).isEqualTo(false);
            assertThat(body.get("code")).isEqualTo(403);
            assertThat(body.get("message")).isEqualTo("Current principal is not allowed to access this resource.");
        });
    }

    @Test
    void disabledPropertyDoesNotActivateAdapter() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CocoSecurityJwtAutoConfiguration.class))
                .withPropertyValues(
                        "coco.security.jwt.enabled=false",
                        "coco.security.jwt.issuer-uri=https://idp.example.com/issuer",
                        "coco.security.jwt.jwk-set-uri=https://idp.example.com/issuer/jwks")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoSecurityJwtProperties.class);
                    assertThat(context).doesNotHaveBean("cocoSecurityJwtMessageBundleRegistrar");
                });
    }

    @Test
    void disabledWebFeatureDoesNotActivateAdapter() {
        this.contextRunner
                .withPropertyValues("coco.features.disabled=web")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoSecurityJwtProperties.class);
                    assertThat(context).doesNotHaveBean("cocoSecurityJwtMessageBundleRegistrar");
                });
    }

    @Test
    void disabledSecurityFeatureDoesNotActivateAdapter() {
        this.contextRunner
                .withPropertyValues("coco.features.disabled=security")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoSecurityJwtProperties.class);
                    assertThat(context).doesNotHaveBean("cocoSecurityJwtMessageBundleRegistrar");
                });
    }

    @Test
    void missingResourceServerDependencyBacksOffWithoutLoadingConfiguration() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(JwtDecoder.class))
                .withConfiguration(AutoConfigurations.of(CocoSecurityJwtAutoConfiguration.class))
                .withPropertyValues(
                        "coco.security.jwt.enabled=true",
                        "coco.security.jwt.issuer-uri=https://idp.example.com/issuer",
                        "coco.security.jwt.jwk-set-uri=https://idp.example.com/issuer/jwks")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CocoSecurityJwtProperties.class);
                    assertThat(context).doesNotHaveBean("cocoSecurityJwtMessageBundleRegistrar");
                });
    }

    @Test
    void missingOAuth2ValidatorDependencyBacksOffWithoutLoadingConfiguration() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(OAuth2TokenValidator.class))
                .withConfiguration(AutoConfigurations.of(CocoSecurityJwtAutoConfiguration.class))
                .withPropertyValues(
                        "coco.security.jwt.enabled=true",
                        "coco.security.jwt.issuer-uri=https://idp.example.com/issuer",
                        "coco.security.jwt.jwk-set-uri=https://idp.example.com/issuer/jwks")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CocoSecurityJwtProperties.class);
                    assertThat(context).doesNotHaveBean("cocoSecurityJwtMessageBundleRegistrar");
                });
    }

    @Test
    void enabledWithOnlyJwkSetUriStartsAndUsesTheConfiguredJwkEndpoint() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CocoSecurityJwtAutoConfiguration.class))
                .withPropertyValues(
                        "coco.security.jwt.enabled=true",
                        "coco.security.jwt.jwk-set-uri=https://idp.example.com/issuer/jwks")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                });
    }

    @Test
    void issuerOnlyConfigurationFailsFastWhenIssuerMetadataCannotBeResolved() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CocoSecurityJwtAutoConfiguration.class))
                .withPropertyValues(
                        "coco.security.jwt.enabled=true",
                        "coco.security.jwt.issuer-uri=http://127.0.0.1:1/unavailable")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "Unable to resolve the Configuration with the provided Issuer");
                });
    }

    @Test
    void enabledWithoutIssuerOrJwkSetUriFailsFast() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CocoSecurityJwtAutoConfiguration.class))
                .withPropertyValues("coco.security.jwt.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "coco.security.jwt.issuer-uri or coco.security.jwt.jwk-set-uri is required when enabled");
                });
    }

    private static Jwt jwt() {
        Instant now = Instant.parse("2026-08-08T08:00:00Z");
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .issuer("https://idp.example.com/issuer")
                .subject("1001")
                .audience(List.of("orders-api"))
                .build();
    }

    private static int indexOf(SecurityFilterChain chain, Class<?> filterType) {
        for (int i = 0; i < chain.getFilters().size(); i++) {
            if (filterType.isInstance(chain.getFilters().get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class SecurityInfrastructure {
    }
}
