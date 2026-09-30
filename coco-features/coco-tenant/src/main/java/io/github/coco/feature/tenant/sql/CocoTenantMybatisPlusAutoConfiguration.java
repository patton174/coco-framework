package io.github.coco.feature.tenant.sql;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.tenant.CocoTenantAutoConfiguration;
import io.github.coco.feature.tenant.CocoTenantProperties;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 *   <li>模块：{@code coco-feature-tenant}</li>
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
     * 创建 MyBatis-Plus 租户拦截器定制器。
     * </p>
     * @param properties 租户功能配置
     * @param contextResolver 租户上下文解析器
     * @param expressionResolver 租户 ID SQL 表达式解析器
     * @param eventPublisherProvider 拦截器忽略治理事件发布器提供器
     * @return MyBatis-Plus 拦截器定制器
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
        return interceptor -> {
            interceptor.addInnerInterceptor(interceptorIgnoreGuard);
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        };
    }
}
