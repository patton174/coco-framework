package io.github.coco.feature.idempotency;

import java.time.Clock;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
    CocoIdempotencyKeyResolver cocoIdempotencyKeyResolver(CocoIdempotencyProperties properties) { return new DefaultCocoIdempotencyKeyResolver(properties); }
    @Bean(destroyMethod = "close") @ConditionalOnMissingBean
    CocoIdempotencyStore cocoIdempotencyStore(CocoIdempotencyProperties properties, @Qualifier("cocoIdempotencyClock") Clock clock) { return new InMemoryCocoIdempotencyStore(properties, clock, true); }
    @Bean @ConditionalOnMissingBean
    CocoIdempotencyResponseWriter cocoIdempotencyResponseWriter(CocoMessageService messages, ObjectMapper mapper) { return new DefaultCocoIdempotencyResponseWriter(messages, mapper); }
    @Bean @ConditionalOnMissingBean(name = "cocoIdempotencyMvcConfigurer")
    WebMvcConfigurer cocoIdempotencyMvcConfigurer(CocoIdempotencyProperties properties, CocoIdempotencyKeyResolver keyResolver,
            CocoIdempotencyStore store, CocoIdempotencyResponseWriter writer, @Qualifier("cocoIdempotencyClock") Clock clock) {
        CocoIdempotencyMvcInterceptor interceptor = new CocoIdempotencyMvcInterceptor(properties, keyResolver, store, writer, clock);
        return new WebMvcConfigurer() { @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor).order(MVC_INTERCEPTOR_ORDER); } };
    }
}
