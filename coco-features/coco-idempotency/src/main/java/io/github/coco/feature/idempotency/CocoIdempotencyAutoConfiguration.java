package io.github.coco.feature.idempotency;

import java.time.Clock;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.web.CocoWebContextAutoConfiguration;
import io.github.coco.feature.web.context.CocoSensitiveRequestHeaderContributor;
import io.github.coco.feature.web.exception.CocoWebErrorResponseWriter;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Coco 请求幂等自动配置。 */
@AutoConfiguration
@AutoConfigureBefore(CocoWebContextAutoConfiguration.class)
@EnableConfigurationProperties(CocoIdempotencyProperties.class)
@ConditionalOnCocoFeature(CocoFeature.IDEMPOTENCY)
@ConditionalOnProperty(prefix = "coco.idempotency", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CocoIdempotencyAutoConfiguration {
    /** 限流先执行，幂等随后获得租约。 */
    static final int MVC_INTERCEPTOR_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    @Bean @ConditionalOnMissingBean(name = "cocoIdempotencyMessageBundleRegistrar")
    CocoMessageBundleRegistrar cocoIdempotencyMessageBundleRegistrar() { return registry -> registry.add("coco-idempotency-messages"); }
    @Bean("cocoIdempotencyClock") @ConditionalOnMissingBean(name = "cocoIdempotencyClock")
    Clock cocoIdempotencyClock() { return Clock.systemUTC(); }
    @Bean @ConditionalOnMissingBean
    CocoIdempotencyOperationResolver cocoIdempotencyOperationResolver() { return new DefaultCocoIdempotencyOperationResolver(); }
    @Bean @ConditionalOnMissingBean
    CocoIdempotencyKeyResolver cocoIdempotencyKeyResolver(CocoIdempotencyProperties properties,
            CocoIdempotencyOperationResolver operationResolver) { return new DefaultCocoIdempotencyKeyResolver(properties, operationResolver); }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    CocoIdempotencyStore cocoIdempotencyStore(CocoIdempotencyProperties properties, @Qualifier("cocoIdempotencyClock") Clock clock) { return new InMemoryCocoIdempotencyStore(properties, clock, true); }
    @Bean @ConditionalOnMissingBean
    CocoIdempotencyResponseWriter cocoIdempotencyResponseWriter(CocoWebErrorResponseWriter writer) { return new DefaultCocoIdempotencyResponseWriter(writer); }
    @Bean @ConditionalOnMissingBean(name = "cocoIdempotencySensitiveHeaderContributor")
    CocoSensitiveRequestHeaderContributor cocoIdempotencySensitiveHeaderContributor(CocoIdempotencyProperties properties) {
        return () -> java.util.Set.of(properties.getHeaderName());
    }
    @Bean @ConditionalOnMissingBean(name = "cocoIdempotencyMvcConfigurer")
    WebMvcConfigurer cocoIdempotencyMvcConfigurer(CocoIdempotencyProperties properties, CocoIdempotencyKeyResolver keyResolver,
            CocoIdempotencyStore store, CocoIdempotencyResponseWriter writer, @Qualifier("cocoIdempotencyClock") Clock clock) {
        CocoIdempotencyMvcInterceptor interceptor = new CocoIdempotencyMvcInterceptor(properties, keyResolver, store, writer, clock);
        return new WebMvcConfigurer() { @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor).order(MVC_INTERCEPTOR_ORDER); } };
    }
}
