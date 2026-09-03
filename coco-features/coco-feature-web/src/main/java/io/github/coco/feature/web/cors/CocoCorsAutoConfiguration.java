package io.github.coco.feature.web.cors;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Coco CORS 跨域自动配置。
 * <p>
 * 当 {@code coco.web.cors.enabled=true} 时，注册全局 {@link CorsFilter} 以处理跨域请求。
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
@ConditionalOnProperty(prefix = "coco.web.cors", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoCorsProperties.class)
public class CocoCorsAutoConfiguration {

    /**
     * <p>
     * 创建 Coco 全局 CORS 过滤器。
     * </p>
     * @param properties CORS 跨域配置属性
     * @return CORS 过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public CorsFilter cocoCorsFilter(CocoCorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(properties.getAllowedMethods());
        config.setAllowedHeaders(properties.getAllowedHeaders());
        config.setExposedHeaders(properties.getExposedHeaders());
        config.setAllowCredentials(properties.isAllowCredentials());
        config.setMaxAge(properties.getMaxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
