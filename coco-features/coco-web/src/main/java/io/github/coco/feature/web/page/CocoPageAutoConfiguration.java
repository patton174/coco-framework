package io.github.coco.feature.web.page;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Coco Web 分页拦截器自动配置。
 * <p>
 * 当应用为 Servlet Web 环境且 {@code coco.web.page.enabled} 属性为 {@code true}（默认）时，
 * 自动注册 {@link CocoPageInterceptor}，在请求入口解析分页参数并写入上下文。
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
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "coco.web.page", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CocoPageProperties.class)
public class CocoPageAutoConfiguration {

    /**
     * <p>
     * 注册分页拦截器 MVC 配置器。
     * </p>
     * @param properties 分页配置属性
     * @return 分页 MVC 配置器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoPageMvcConfigurer")
    public WebMvcConfigurer cocoPageMvcConfigurer(CocoPageProperties properties) {
        return new CocoPageWebMvcConfigurer(properties);
    }

    private static final class CocoPageWebMvcConfigurer implements WebMvcConfigurer {

        private final CocoPageInterceptor interceptor;

        private CocoPageWebMvcConfigurer(CocoPageProperties properties) {
            this.interceptor = new CocoPageInterceptor(properties);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(this.interceptor);
        }
    }
}
