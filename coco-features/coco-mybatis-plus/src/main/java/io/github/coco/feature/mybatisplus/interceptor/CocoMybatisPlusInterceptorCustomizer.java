package io.github.coco.feature.mybatisplus.interceptor;

import java.util.Comparator;
import java.util.Objects;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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
     * @return 定制器执行顺序
     */
    default int getOrder() {
        Integer annotatedOrder = OrderUtils.getOrder(getClass());
        return annotatedOrder == null ? USER_ORDER : annotatedOrder;
    }

    /**
     * <p>
     * 返回同一顺序内的稳定排序键。
     * </p>
     * @return 稳定排序键
     */
    default String getOrderKey() {
        return getClass().getName();
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

    final class OrderedCustomizer implements CocoMybatisPlusInterceptorCustomizer {

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
