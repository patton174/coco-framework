package io.github.coco.feature.mybatisplus;

import io.github.coco.feature.mybatisplus.pagination.CocoMybatisPlusPaginationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco MyBatis-Plus 功能配置属性。
 * <p>
 * 绑定 {@code coco.mybatis-plus} 命名空间，集中维护 MyBatis-Plus 集成、分页拦截器和后续 SQL 扩展能力的配置。
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
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.mybatis-plus")
public class CocoMybatisPlusProperties {

    @NestedConfigurationProperty
    private CocoMybatisPlusPaginationProperties pagination = new CocoMybatisPlusPaginationProperties();

    /**
     * <p>
     * 返回分页拦截器配置属性。
     * </p>
     * @return 分页拦截器配置属性
     */
    public CocoMybatisPlusPaginationProperties getPagination() {
        return this.pagination;
    }

    /**
     * <p>
     * 设置分页拦截器配置属性。
     * </p>
     * @param pagination 分页拦截器配置属性
     */
    public void setPagination(CocoMybatisPlusPaginationProperties pagination) {
        this.pagination = pagination == null ? new CocoMybatisPlusPaginationProperties() : pagination;
    }
}
