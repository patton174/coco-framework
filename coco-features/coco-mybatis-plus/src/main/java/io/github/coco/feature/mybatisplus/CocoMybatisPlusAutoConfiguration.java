package io.github.coco.feature.mybatisplus;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorFactory;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco MyBatis-Plus 功能自动配置。
 * <p>
 * 负责为 MyBatis-Plus 功能模块注册国际化消息资源、默认分页拦截器和 SQL 拦截器扩展点。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class,
        before = MybatisPlusInnerInterceptorAutoConfiguration.class)
@ConditionalOnCocoFeature(CocoFeature.MYBATIS_PLUS)
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(CocoMybatisPlusProperties.class)
public class CocoMybatisPlusAutoConfiguration {

    /**
     * <p>
     * 注册 MyBatis-Plus 功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoMybatisPlusMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoMybatisPlusMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-mybatis-plus-messages");
    }

    /**
     * <p>
     * 创建 Coco 托管的 MyBatis-Plus 拦截器。
     * </p>
     * @param properties MyBatis-Plus 功能配置属性
     * @param innerInterceptors 业务或其他框架注册的 MyBatis-Plus 内置拦截器集合
     * @param customizers 拦截器定制器集合
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(CocoMybatisPlusProperties properties,
            ObjectProvider<InnerInterceptor> innerInterceptors,
            ObjectProvider<CocoMybatisPlusInterceptorCustomizer> customizers) {
        return new CocoMybatisPlusInterceptorFactory(properties, innerInterceptors, customizers).create();
    }
}
