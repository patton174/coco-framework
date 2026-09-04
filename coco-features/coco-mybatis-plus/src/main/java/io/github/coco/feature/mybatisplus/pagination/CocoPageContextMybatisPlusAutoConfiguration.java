package io.github.coco.feature.mybatisplus.pagination;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import io.github.coco.context.CocoPageContextHolder;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Coco 分页上下文 MyBatis-Plus 自动配置。
 * <p>
 * 注册 {@link CocoPageContextInnerInterceptor} 到 MyBatis-Plus 拦截器链，
 * 在查询执行前自动从 {@link CocoPageContextHolder} 读取分页和排序参数注入到 {@code IPage} 对象中。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
@AutoConfiguration(after = CocoMybatisPlusAutoConfiguration.class)
@ConditionalOnClass({InnerInterceptor.class, CocoMybatisPlusInterceptorCustomizer.class})
@ConditionalOnProperty(prefix = "coco.mybatis-plus.pagination.context", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CocoPageContextMybatisPlusAutoConfiguration {

    /**
     * <p>
     * 注册分页上下文拦截器定制器。
     * </p>
     * @return MyBatis-Plus 拦截器定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoPageContextInterceptorCustomizer")
    public CocoMybatisPlusInterceptorCustomizer cocoPageContextInterceptorCustomizer() {
        return interceptor -> interceptor.addInnerInterceptor(new CocoPageContextInnerInterceptor());
    }
}
