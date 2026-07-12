package io.github.coco.feature.datapermission.mybatisplus;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration;
import io.github.coco.feature.datapermission.CocoDataPermissionProperties;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextResolver;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlPredicateProvider;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlProperties;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceResolver;
import io.github.coco.feature.datapermission.sql.DefaultCocoDataPermissionSqlPredicateProvider;
import io.github.coco.feature.datapermission.sql.PropertyCocoDataPermissionSqlResourceResolver;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 数据权限 MyBatis-Plus 自动配置。
 * <p>
 * 在数据权限 SQL 接入开启时，通过 MyBatis-Plus 数据权限拦截器把 Coco 数据权限上下文转换为 SQL 条件。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(after = {CocoDataPermissionAutoConfiguration.class, CocoMybatisPlusAutoConfiguration.class})
@ConditionalOnCocoFeature(CocoFeature.DATA_PERMISSION)
@ConditionalOnClass(name = {
        "com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor",
        "io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer"
})
@EnableConfigurationProperties(CocoDataPermissionProperties.class)
public class CocoDataPermissionMybatisPlusAutoConfiguration {

    /**
     * <p>
     * 创建默认 SQL 资源解析器。
     * </p>
     * @param properties 数据权限配置
     * @return SQL 资源解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoDataPermissionSqlResourceResolver cocoDataPermissionSqlResourceResolver(
            CocoDataPermissionProperties properties) {
        return new PropertyCocoDataPermissionSqlResourceResolver(properties.getSql());
    }

    /**
     * <p>
     * 创建默认 SQL 谓词提供器。
     * </p>
     * @return SQL 谓词提供器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoDataPermissionSqlPredicateProvider cocoDataPermissionSqlPredicateProvider() {
        return new DefaultCocoDataPermissionSqlPredicateProvider();
    }

    /**
     * <p>
     * 创建 MyBatis-Plus 数据权限拦截器定制器。
     * </p>
     * @param properties 数据权限配置
     * @param contextResolver 数据权限上下文解析器
     * @param resourceResolver SQL 资源解析器
     * @param predicateProvider SQL 谓词提供器
     * @return MyBatis-Plus 拦截器定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoDataPermissionMybatisPlusInterceptorCustomizer")
    @ConditionalOnProperty(prefix = "coco.data-permission.sql", name = "enabled", havingValue = "true")
    public CocoMybatisPlusInterceptorCustomizer cocoDataPermissionMybatisPlusInterceptorCustomizer(
            CocoDataPermissionProperties properties,
            CocoDataPermissionContextResolver contextResolver,
            CocoDataPermissionSqlResourceResolver resourceResolver,
            CocoDataPermissionSqlPredicateProvider predicateProvider) {
        CocoDataPermissionSqlProperties sqlProperties = properties.getSql();
        CocoMybatisPlusDataPermissionHandler handler = new CocoMybatisPlusDataPermissionHandler(sqlProperties,
                contextResolver, resourceResolver, predicateProvider);
        return interceptor -> interceptor.addInnerInterceptor(new DataPermissionInterceptor(handler));
    }
}
