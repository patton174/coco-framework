package io.github.coco.feature.web.headers;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco 安全响应头自动配置。
 * <p>
 * 注册 {@link CocoSecurityHeadersFilter}，默认写入 {@code X-Content-Type-Options}、{@code X-Frame-Options}、
 * {@code Referrer-Policy} 三个安全响应头。
 * </p>
 * <p>
 * 该模块采用 {@code matchIfMissing = true}，即默认开启，与 CORS 模块的默认关闭策略不同：这些响应头本身是安全的，
 * 只有在默认生效的前提下才能真正起到保护作用；而放开跨域会扩大攻击面，必须由应用显式开启。
 * </p>
 * <p>
 * 默认执行顺序与请求体缓存过滤器同为 {@code Ordered.HIGHEST_PRECEDENCE}。两者顺序相同不会产生问题：它们相互独立，
 * 一个包装请求，另一个只写响应头。应用如需指定确切位置，可通过 {@code coco.web.security-headers.order} 调整。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "coco.web.security-headers", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CocoSecurityHeadersProperties.class)
public class CocoSecurityHeadersAutoConfiguration {

    /**
     * <p>
     * 创建 Coco 安全响应头过滤器注册器。
     * </p>
     * @param properties 安全响应头配置属性
     * @return 安全响应头过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(Filter.class)
    @ConditionalOnMissingBean(name = "cocoSecurityHeadersFilterRegistration")
    public FilterRegistrationBean<CocoSecurityHeadersFilter> cocoSecurityHeadersFilterRegistration(
            CocoSecurityHeadersProperties properties) {
        FilterRegistrationBean<CocoSecurityHeadersFilter> registration = new FilterRegistrationBean<>(
                new CocoSecurityHeadersFilter(properties));
        registration.setName("cocoSecurityHeadersFilter");
        registration.setOrder(properties.getOrder());
        return registration;
    }
}
