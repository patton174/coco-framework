package io.github.coco.feature.httpclient;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.CocoWebProperties;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.payload.DefaultCocoPayloadParameterResolver;
import io.github.coco.feature.web.request.metadata.CocoWebSecurityHeaderNames;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Coco HTTP 客户端自动配置。
 * <p>
 * 本模块不执行自动重试，避免对流式请求体和非幂等请求产生隐藏副作用。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties({ CocoHttpClientProperties.class, CocoWebProperties.class })
@ConditionalOnProperty(prefix = "coco.http", name = "enabled", havingValue = "true", matchIfMissing = true)
@SuppressFBWarnings(value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
        justification = "RestClient status-handler lambdas signal a mapped non-2xx response by throwing it.")
public class CocoHttpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CocoHttpErrorMapper cocoHttpErrorMapper() {
        return new DefaultCocoHttpErrorMapper();
    }

    @Bean
    @ConditionalOnMissingBean(CocoHttpClients.class)
    CocoHttpClients cocoHttpClients(CocoHttpClientProperties properties, CocoWebProperties webProperties,
            CocoHttpErrorMapper errorMapper, ObjectProvider<RestClient.Builder> builderProvider,
            ObjectProvider<CocoHttpClientCustomizer> customizerProvider,
            ObjectProvider<CocoHttpClientSigningCredentialProvider> credentialProvider,
            ObjectProvider<CocoWebRequestCanonicalizer> canonicalizerProvider,
            ListableBeanFactory beanFactory) {
        properties.validate();
        List<CocoHttpClientCustomizer> customizers = customizerProvider.orderedStream().toList();
        List<CocoHttpClientSigningCredentials.Provider> signingProviders = credentialProvider.orderedStream()
                .map(provider -> new CocoHttpClientSigningCredentials.Provider(providerName(provider, beanFactory),
                        provider))
                .toList();
        CocoWebRequestCanonicalizer canonicalizer = canonicalizerProvider.getIfAvailable(() ->
                new DefaultCocoWebRequestCanonicalizer(webProperties.getContext().getCanonicalization(),
                        webProperties.getTrace(), null));
        Map<String, RestClient> clients = new LinkedHashMap<>();
        properties.getClients().forEach((name, client) -> clients.put(name,
                build(name, client, webProperties.getTrace(), errorMapper,
                        builderProvider.getIfAvailable(RestClient::builder), customizers, signingProviders, canonicalizer,
                        webProperties)));
        return new DefaultCocoHttpClients(clients);
    }

    private static RestClient build(String name, CocoHttpClientProperties.Client client,
            CocoTraceProperties traceProperties, CocoHttpErrorMapper errorMapper, RestClient.Builder baseBuilder,
            List<CocoHttpClientCustomizer> customizers,
            List<CocoHttpClientSigningCredentials.Provider> signingProviders,
            CocoWebRequestCanonicalizer canonicalizer, CocoWebProperties webProperties) {
        RestClient.Builder builder = baseBuilder.clone();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(client.getConnectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(client.getReadTimeout());
        builder.baseUrl(client.getBaseUrl()).requestFactory(factory).defaultHeaders(headers ->
                client.getDefaultHeaders().forEach(headers::set));
        customizers.stream().filter(customizer -> customizer.supports(name))
                .forEach(customizer -> customizer.customize(name, builder));
        builder.requestInterceptor((request, body, execution) -> {
            String headerName = traceProperties.getHeaderName();
            if (!request.getHeaders().containsHeader(headerName)) {
                CocoTraceContext.currentTraceId().ifPresent(traceId -> request.getHeaders().set(headerName, traceId));
            }
            return execution.execute(request, body);
        });
        if (client.getSigning().isEnabled()) {
            CocoHttpClientSigningCredential credential = CocoHttpClientSigningCredentials.resolve(name,
                    client.getSigning(), signingProviders);
            Set<String> configuredNames = client.getSigning().getCanonicalHeaderNames().isEmpty()
                    ? webProperties.getContext().getCanonicalHeaderNames()
                    : client.getSigning().getCanonicalHeaderNames();
            Set<String> canonicalHeaderNames = CocoWebSecurityHeaderNames.canonicalHeaderNames(configuredNames,
                    clientSignatureProperties(client.getSigning()), webProperties.getEncryption(),
                    webProperties.getReplay());
            DefaultCocoPayloadParameterResolver payloadParameterResolver =
                    new DefaultCocoPayloadParameterResolver(webProperties.getContext().getParameter());
            builder.requestInterceptor(new CocoHttpClientSigningInterceptor(client.getSigning(), credential,
                    canonicalizer, canonicalHeaderNames, payloadParameterResolver));
        }
        builder.defaultStatusHandler(status -> !status.is2xxSuccessful(),
                (request, response) -> { throw errorMapper.map(name, request, response); });
        return builder.build();
    }

    private static io.github.coco.feature.web.signature.CocoSignatureProperties clientSignatureProperties(
            CocoHttpClientProperties.Signing signing) {
        io.github.coco.feature.web.signature.CocoSignatureProperties properties =
                new io.github.coco.feature.web.signature.CocoSignatureProperties();
        properties.setAppIdHeaderName(signing.getAppIdHeaderName());
        properties.setKeyIdHeaderName(signing.getKeyIdHeaderName());
        properties.setTimestampHeaderName(signing.getTimestampHeaderName());
        properties.setNonceHeaderName(signing.getNonceHeaderName());
        properties.setSignatureHeaderName(signing.getSignatureHeaderName());
        properties.setAlgorithmHeaderName(signing.getAlgorithmHeaderName());
        return properties;
    }

    private static String providerName(CocoHttpClientSigningCredentialProvider provider,
            ListableBeanFactory beanFactory) {
        for (String beanName : beanFactory.getBeanNamesForType(CocoHttpClientSigningCredentialProvider.class)) {
            if (beanFactory.getBean(beanName) == provider) {
                return beanName;
            }
        }
        String typeName = provider.getClass().getName();
        int runtimeSuffix = typeName.indexOf('/');
        return runtimeSuffix < 0 ? typeName : typeName.substring(0, runtimeSuffix);
    }
}
