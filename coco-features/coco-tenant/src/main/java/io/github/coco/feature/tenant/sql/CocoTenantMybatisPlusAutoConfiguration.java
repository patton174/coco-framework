package io.github.coco.feature.tenant.sql;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.tenant.CocoTenantAutoConfiguration;
import io.github.coco.feature.tenant.CocoTenantProperties;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;
import io.github.coco.i18n.CocoMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Coco 租户 MyBatis-Plus 自动配置。
 * <p>
 * 当 MyBatis-Plus SQL 拦截能力可用时，向 Coco MyBatis-Plus 拦截器工厂注册租户行隔离拦截器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = {CocoTenantAutoConfiguration.class, CocoMybatisPlusAutoConfiguration.class})
@ConditionalOnCocoFeature(CocoFeature.TENANT)
@ConditionalOnClass({TenantLineInnerInterceptor.class, CocoMybatisPlusInterceptorCustomizer.class})
@ConditionalOnProperty(prefix = "coco.tenant.sql", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CocoTenantProperties.class)
public class CocoTenantMybatisPlusAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoTenantMybatisPlusAutoConfiguration.class);

    private static final String LEGACY_PATTERNS_WARNING =
            "coco.feature.tenant.warn.interceptor-ignore-legacy-patterns";

    private static final String LEGACY_PATTERNS_STRICT_ERROR =
            "coco.feature.tenant.error.interceptor-ignore-legacy-patterns-strict";

    /**
     * <p>
     * 创建默认租户 ID SQL 表达式解析器。
     * </p>
     * @return 租户 ID SQL 表达式解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoTenantIdExpressionResolver cocoTenantIdExpressionResolver() {
        return new DefaultCocoTenantIdExpressionResolver();
    }

    /**
     * <p>
     * 创建租户 SQL 隔离旁路治理事件发布器。
     * </p>
     * <p>
     * 默认将结构化事件交给 Spring 应用事件总线，业务可以通过同类型 Bean 替换为审计、告警或指标实现。
     * </p>
     * @param applicationEventPublisher Spring 应用事件发布器
     * @return 租户 SQL 隔离旁路治理事件发布器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoTenantInterceptorIgnoreEventPublisher cocoTenantInterceptorIgnoreEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        return applicationEventPublisher::publishEvent;
    }

    /**
     * <p>
     * 创建旧模式白名单启动校验器。
     * </p>
     * <p>
     * 兼容模式下发布结构化事件并记录废弃警告；严格模式下在应用完成启动前拒绝旧配置。
     * </p>
     * @param properties 租户功能配置
     * @param applicationEventPublisher Spring 应用事件发布器
     * @param messageService Coco 消息服务
     * @return 启动校验回调
     */
    @Bean
    public SmartInitializingSingleton cocoTenantInterceptorIgnoreLegacyConfigurationValidator(
            CocoTenantProperties properties,
            ApplicationEventPublisher applicationEventPublisher,
            CocoMessageService messageService) {
        return () -> validateLegacyPatterns(properties.getSql().getInterceptorIgnore(),
                applicationEventPublisher, messageService);
    }

    /**
     * <p>
     * 创建 MyBatis-Plus 租户拦截器定制器。
     * </p>
     * @param properties 租户功能配置
     * @param contextResolver 租户上下文解析器
     * @param expressionResolver 租户 ID SQL 表达式解析器
     * @param eventPublisherProvider 拦截器忽略治理事件发布器提供器
     * @return MyBatis-Plus 租户拦截器定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoTenantMybatisPlusInterceptorCustomizer")
    public CocoMybatisPlusInterceptorCustomizer cocoTenantMybatisPlusInterceptorCustomizer(
            CocoTenantProperties properties,
            CocoTenantContextResolver contextResolver,
            CocoTenantIdExpressionResolver expressionResolver,
            ObjectProvider<CocoTenantInterceptorIgnoreEventPublisher> eventPublisherProvider) {
        CocoTenantInterceptorIgnoreGuard interceptorIgnoreGuard = new CocoTenantInterceptorIgnoreGuard(
                properties.getSql(),
                eventPublisherProvider.getIfAvailable(NoOpCocoTenantInterceptorIgnoreEventPublisher::new));
        CocoTenantLineHandler tenantLineHandler = new CocoTenantLineHandler(properties.getSql(),
                contextResolver, expressionResolver);
        return CocoMybatisPlusInterceptorCustomizer.ordered(
                CocoMybatisPlusInterceptorCustomizer.TENANT_INTERCEPTOR_IGNORE_GUARD_ORDER,
                interceptor -> {
                    interceptor.addInnerInterceptor(interceptorIgnoreGuard);
                    interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
                });
    }

    @SuppressWarnings("deprecation")
    private static void validateLegacyPatterns(CocoTenantInterceptorIgnoreProperties properties,
            ApplicationEventPublisher applicationEventPublisher, CocoMessageService messageService) {
        Set<String> patterns = properties.getAllowedMappedStatements().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (patterns.isEmpty()) {
            return;
        }

        boolean strictMode = properties.isStrictMode();
        applicationEventPublisher.publishEvent(
                new CocoTenantInterceptorIgnoreLegacyConfigurationEvent(patterns, strictMode));
        String messageCode = strictMode ? LEGACY_PATTERNS_STRICT_ERROR : LEGACY_PATTERNS_WARNING;
        String message = messageService.getMessage(messageCode, String.join(", ", patterns));
        LOGGER.warn("event=coco_tenant_legacy_interceptor_ignore_configuration strictMode={} patterns={} message={}",
                strictMode, patterns, message);
        if (strictMode) {
            throw new IllegalStateException(message);
        }
    }
}
