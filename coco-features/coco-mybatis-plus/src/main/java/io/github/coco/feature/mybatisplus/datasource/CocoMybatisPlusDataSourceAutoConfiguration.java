package io.github.coco.feature.mybatisplus.datasource;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusProperties;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Coco MyBatis-Plus 动态多数据源自动配置。
 * <p>
 * 在 {@link DataSourceAutoConfiguration} 与 {@link MybatisPlusAutoConfiguration} 之前运行，
 * 暴露全局唯一的 {@code @Primary} {@link CocoRoutingDataSource} 以及 {@link CocoDataSource} 声明式路由切面与 SPI 组件。
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
@AutoConfiguration(before = {
        DataSourceAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
})
@ConditionalOnCocoFeature(CocoFeature.MYBATIS_PLUS)
@ConditionalOnClass({DataSource.class, AbstractRoutingDataSource.class})
@ConditionalOnProperty(prefix = "coco.mybatis-plus.datasource", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoMybatisPlusProperties.class)
public class CocoMybatisPlusDataSourceAutoConfiguration {

    /**
     * <p>
     * 注册默认物理数据源创建器 SPI 实现。
     * </p>
     * @return 数据源创建器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoDataSourceCreator cocoDataSourceCreator() {
        return new DefaultCocoDataSourceCreator();
    }

    /**
     * <p>
     * 注册基于 {@code coco.mybatis-plus.datasource.sources} 配置属性的数据源提供者。
     * </p>
     * @param properties MyBatis-Plus 配置属性
     * @param creator 数据源创建器
     * @return 声明式配置数据源提供者
     */
    @Bean
    @ConditionalOnMissingBean(name = "propertiesCocoDataSourceProvider")
    public CocoDataSourceProvider propertiesCocoDataSourceProvider(
            CocoMybatisPlusProperties properties,
            CocoDataSourceCreator creator) {
        return new PropertiesCocoDataSourceProvider(properties.getDatasource(), creator);
    }

    /**
     * <p>
     * 注册默认的数据源分组负载均衡器。
     * </p>
     * @param properties MyBatis-Plus 配置属性
     * @return 分组负载均衡器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoDataSourceLoadBalancer cocoDataSourceLoadBalancer(CocoMybatisPlusProperties properties) {
        return switch (properties.getDatasource().getLoadBalanceStrategy()) {
            case RANDOM -> new RandomCocoDataSourceLoadBalancer();
            case ROUND_ROBIN -> new RoundRobinCocoDataSourceLoadBalancer();
        };
    }

    /**
     * <p>
     * 注册默认的数据源当前路由键决策器。
     * </p>
     * @return 数据源路由键决策器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoDataSourceRouter cocoDataSourceRouter() {
        return new ContextCocoDataSourceRouter();
    }

    /**
     * <p>
     * 注册全局唯一的主动态路由数据源 {@link CocoRoutingDataSource}。
     * </p>
     * @param properties MyBatis-Plus 配置属性
     * @param providers 数据源提供者集合
     * @param loadBalancer 分组负载均衡器
     * @param router 数据源路由键决策器
     * @return 动态路由数据源
     */
    @Primary
    @Bean(name = {"dataSource", "cocoRoutingDataSource"}, destroyMethod = "close")
    @ConditionalOnMissingBean(CocoRoutingDataSource.class)
    public CocoRoutingDataSource cocoRoutingDataSource(
            CocoMybatisPlusProperties properties,
            ObjectProvider<CocoDataSourceProvider> providers,
            CocoDataSourceLoadBalancer loadBalancer,
            CocoDataSourceRouter router) {
        List<CocoDataSourceProvider> orderedProviders = providers.orderedStream().toList();
        return new CocoRoutingDataSource(properties.getDatasource(), orderedProviders, loadBalancer, router);
    }

    /**
     * <p>
     * 注册默认的数据源路由键 SpEL 解析器。
     * </p>
     * @param properties MyBatis-Plus 配置属性
     * @return 路由键解析器
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    public CocoDataSourceKeyResolver cocoDataSourceKeyResolver(CocoMybatisPlusProperties properties) {
        return new DefaultCocoDataSourceKeyResolver(properties.getDatasource());
    }

    /**
     * <p>
     * 注册 {@link CocoDataSource} 声明式切换切面。
     * </p>
     * @param keyResolver 路由键解析器
     * @param properties MyBatis-Plus 配置属性
     * @return 数据源切换切面
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean
    public CocoDataSourceAspect cocoDataSourceAspect(
            CocoDataSourceKeyResolver keyResolver,
            CocoMybatisPlusProperties properties) {
        return new CocoDataSourceAspect(keyResolver, properties.getDatasource());
    }

    /**
     * <p>
     * 注册识别类、方法、接口及 MyBatis Mapper 代理的 {@link CocoDataSource} Advisor。
     * </p>
     * @param aspect 数据源切换切面
     * @param properties MyBatis-Plus 配置属性
     * @return 切面通知器
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "cocoDataSourceAdvisor")
    public DefaultPointcutAdvisor cocoDataSourceAdvisor(
            CocoDataSourceAspect aspect,
            CocoMybatisPlusProperties properties) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(new CocoDataSourcePointcut(), aspect);
        advisor.setOrder(properties.getDatasource().getAspectOrder());
        return advisor;
    }
}
