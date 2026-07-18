package io.github.coco.feature.mybatisplus.interceptor;

import java.util.Comparator;
import java.util.Objects;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;

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
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoMybatisPlusInterceptorCustomizer {

    /**
     * 数据权限 SQL 条件阶段的顺序。
     */
    int DATA_PERMISSION_ORDER = -300;

    /**
     * 租户忽略治理 guard 阶段的顺序。
     */
    int TENANT_INTERCEPTOR_IGNORE_GUARD_ORDER = -200;

    /**
     * 租户行隔离阶段的顺序。
     */
    int TENANT_LINE_ORDER = -100;

    /**
     * 应用自定义器的默认顺序。
     */
    int USER_ORDER = 0;

    /**
     * <p>
     * 定制 Coco 托管的 MyBatis-Plus 拦截器。
     * </p>
     * @param interceptor MyBatis-Plus 拦截器
     */
    void customize(MybatisPlusInterceptor interceptor);

    /**
     * <p>
     * 返回定制器执行顺序。
     * </p>
     * <p>
     * 框架阶段依次为数据权限、租户忽略治理、租户行隔离和应用定制；SQL guard 与分页由工厂在全部
     * 定制器之后追加。该默认方法保持既有 lambda 和已编译实现的二进制兼容，应用可覆写该方法或使用
     * {@link org.springframework.core.annotation.Order @Order} 覆盖默认顺序。
     * </p>
     * <p>
     * 顺序优先级为显式覆写的 {@code getOrder()}（包括 {@link Ordered} 和 {@link #ordered(int,
     * CocoMybatisPlusInterceptorCustomizer)}）、Spring 的 {@code @Order}（包括 {@code @Bean}
     * 工厂方法）和 {@link #USER_ORDER}。同一优先级内按数值升序；Spring 提供器中的未显式覆写实例保留
     * {@link org.springframework.beans.factory.ObjectProvider#orderedStream()} 的稳定顺序。
     * </p>
     * @return 定制器执行顺序
     */
    default int getOrder() {
        Integer annotatedOrder = OrderUtils.getOrder(resolveUserClass(this));
        return annotatedOrder == null ? USER_ORDER : annotatedOrder;
    }

    /**
     * <p>
     * 返回同一顺序内的稳定排序键。
     * </p>
     * @return 稳定排序键
     */
    default String getOrderKey() {
        return resolveUserClass(this).getName();
    }

    /**
     * <p>
     * 创建指定顺序的定制器包装器。
     * </p>
     * @param order 定制器执行顺序
     * @param customizer 实际定制器
     * @return 带顺序的定制器
     */
    static CocoMybatisPlusInterceptorCustomizer ordered(int order, CocoMybatisPlusInterceptorCustomizer customizer) {
        return new OrderedCustomizer(order, customizer);
    }

    /**
     * <p>
     * 返回统一的稳定排序器。
     * </p>
     * @return 定制器排序器
     */
    static Comparator<CocoMybatisPlusInterceptorCustomizer> orderComparator() {
        return Comparator.comparingInt(CocoMybatisPlusInterceptorCustomizer::getOrder)
                .thenComparing(CocoMybatisPlusInterceptorCustomizer::getOrderKey);
    }

    /**
     * <p>
     * 返回 Spring 提供器使用的排序器。
     * </p>
     * <p>
     * Spring 已经在输入流中解析了类级和 {@code @Bean} 方法级 {@code @Order}。显式覆写
     * {@code getOrder()} 的实例优先于该 Spring 元数据；未显式覆写的实例返回零比较结果，保持输入流顺序。
     * </p>
     * @return Spring customizer 排序器
     */
    static Comparator<CocoMybatisPlusInterceptorCustomizer> springOrderComparator() {
        return orderComparator();
    }

    static boolean hasExplicitOrder(CocoMybatisPlusInterceptorCustomizer customizer) {
        if (customizer instanceof Ordered) {
            return true;
        }
        try {
            return resolveUserClass(customizer).getMethod("getOrder").getDeclaringClass()
                    != CocoMybatisPlusInterceptorCustomizer.class;
        }
        catch (NoSuchMethodException ex) {
            return false;
        }
    }

    static Class<?> resolveUserClass(CocoMybatisPlusInterceptorCustomizer customizer) {
        Class<?> targetClass = AopUtils.getTargetClass(Objects.requireNonNull(customizer, "customizer must not be null"));
        return targetClass == null ? customizer.getClass() : targetClass;
    }

    final class OrderedCustomizer implements CocoMybatisPlusInterceptorCustomizer, Ordered {

        private final int order;

        private final CocoMybatisPlusInterceptorCustomizer delegate;

        private OrderedCustomizer(int order, CocoMybatisPlusInterceptorCustomizer delegate) {
            this.order = order;
            this.delegate = Objects.requireNonNull(delegate, "customizer must not be null");
        }

        @Override
        public void customize(MybatisPlusInterceptor interceptor) {
            this.delegate.customize(interceptor);
        }

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public String getOrderKey() {
            return this.delegate.getOrderKey();
        }
    }
}
