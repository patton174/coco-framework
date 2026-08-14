package io.github.coco.feature.mybatisplus.interceptor;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;

/**
 * Coco MyBatis-Plus 拦截器定制器。
 * <p>
 * 允许租户、数据权限、审计等框架模块向 Coco 托管的 {@link MybatisPlusInterceptor} 注册自己的内置拦截器。
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
@FunctionalInterface
public interface CocoMybatisPlusInterceptorCustomizer {

    /**
     * <p>
     * 定制 Coco 托管的 MyBatis-Plus 拦截器。
     * </p>
     * @param interceptor MyBatis-Plus 拦截器
     */
    void customize(MybatisPlusInterceptor interceptor);
}
