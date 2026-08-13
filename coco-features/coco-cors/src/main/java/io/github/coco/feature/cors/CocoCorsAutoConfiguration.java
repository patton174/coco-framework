package io.github.coco.feature.cors;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Coco Servlet CORS 自动配置。
 * <p>
 * 仅在 {@code coco.cors.enabled=true} 且 Web 功能启用时注册 Spring 标准
 * {@link CorsConfigurationSource} 与 {@link CorsFilter}。应用自行提供上述 Bean 或
 * {@code FilterRegistrationBean<CorsFilter>} 时，本配置整体退避，由应用完全接管 CORS 行为，
 * 避免产生重复的跨域响应头。
 * </p>
 *
 * @author patton174
 * @since 2.0.1
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnProperty(prefix = "coco.cors", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(value = { CorsConfigurationSource.class, CorsFilter.class },
        parameterizedContainer = FilterRegistrationBean.class)
@EnableConfigurationProperties(CocoCorsProperties.class)
public class CocoCorsAutoConfiguration {

    /**
     * 创建基于路径映射的 Spring CORS 配置源。
     *
     * @param properties CORS 配置属性
     * @return Spring CORS 配置源
     */
    @Bean(name = "cocoCorsConfigurationSource")
    public CorsConfigurationSource cocoCorsConfigurationSource(CocoCorsProperties properties) {
        return CocoCorsConfigurationFactory.create(properties);
    }

    /**
     * 创建最早执行的 Spring CORS Filter 注册器。
     *
     * @param configurationSource Spring CORS 配置源
     * @return CORS Filter 注册器
     */
    @Bean(name = "cocoCorsFilterRegistration")
    public FilterRegistrationBean<CorsFilter> cocoCorsFilterRegistration(CorsConfigurationSource configurationSource) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(
                new CorsFilter(configurationSource));
        registration.setName("cocoCorsFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
