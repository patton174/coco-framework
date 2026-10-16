package io.github.coco.feature.mybatisplus.datasource;

import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

/**
 * 匹配声明了 {@link CocoDataSource} 的方法、类型或接口（含 MyBatis Mapper 接口）的静态切入点。
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
public final class CocoDataSourcePointcut extends StaticMethodMatcherPointcut {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method != null && CocoDataSourceAspect.hasDataSource(method, targetClass);
    }
}
