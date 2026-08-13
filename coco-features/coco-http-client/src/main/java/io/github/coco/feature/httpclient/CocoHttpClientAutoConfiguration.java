package io.github.coco.feature.httpclient;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.CocoWebProperties;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import org.springframework.beans.factory.ObjectProvider;
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
            ObjectProvider<CocoHttpClientCustomizer> customizerProvider) {
        properties.validate();
        List<CocoHttpClientCustomizer> customizers = customizerProvider.orderedStream().toList();
        Map<String, RestClient> clients = new LinkedHashMap<>();
        properties.getClients().forEach((name, client) -> clients.put(name,
                build(name, client, webProperties.getTrace(), errorMapper,
                        builderProvider.getIfAvailable(RestClient::builder), customizers)));
        return new DefaultCocoHttpClients(clients);
    }

    private static RestClient build(String name, CocoHttpClientProperties.Client client,
            CocoTraceProperties traceProperties, CocoHttpErrorMapper errorMapper, RestClient.Builder baseBuilder,
            List<CocoHttpClientCustomizer> customizers) {
        RestClient.Builder builder = baseBuilder.clone();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(client.getConnectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(client.getReadTimeout());
        builder.baseUrl(client.getBaseUrl()).requestFactory(factory).defaultHeaders(headers ->
                client.getDefaultHeaders().forEach(headers::set));
        builder.requestInterceptor((request, body, execution) -> {
            String headerName = traceProperties.getHeaderName();
            if (!request.getHeaders().containsHeader(headerName)) {
                CocoTraceContext.currentTraceId().ifPresent(traceId -> request.getHeaders().set(headerName, traceId));
            }
            return execution.execute(request, body);
        });
        builder.defaultStatusHandler(status -> !status.is2xxSuccessful(),
                (request, response) -> { throw errorMapper.map(name, request, response); });
        customizers.stream().filter(customizer -> customizer.supports(name))
                .forEach(customizer -> customizer.customize(name, builder));
        return builder.build();
    }
}
