package io.github.coco.feature.mybatisplus.pagination;

import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.coco.api.CocoPage;
import io.github.coco.context.CocoSortOrder;
import io.github.coco.context.CocoPageContext;
import io.github.coco.context.CocoPageContextHolder;
import io.github.coco.exception.CocoCommonErrorCode;

/**
 * Coco MyBatis-Plus 分页工具类。
 * <p>
 * 提供 {@link CocoPageContextHolder} 与 MyBatis-Plus {@link Page} 之间的桥接能力，
 * 以及 MyBatis-Plus {@link IPage} 到 {@link CocoPage} 的转换，消除业务代码对分页基础设施的直接依赖。
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
public final class CocoPages {

    private CocoPages() {
    }

    /**
     * <p>
     * 从当前线程的分页上下文中构建 MyBatis-Plus 分页对象。
     * </p>
     * <p>
     * 若当前线程未设置分页上下文，则抛出请求参数不合法异常。
     * </p>
     * @param <T> 数据类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> fromContext() {
        CocoPageContext context = CocoPageContextHolder.current()
                .orElseThrow(() -> CocoCommonErrorCode.INVALID_ARGUMENT.request("page"));
        Page<T> page = Page.of(context.page(), context.size());
        applyOrders(page, context.orders());
        return page;
    }

    /**
     * <p>
     * 根据页码和每页大小构建 MyBatis-Plus 分页对象。
     * </p>
     * @param page 页码（从 1 开始）
     * @param size 每页大小
     * @param <T> 数据类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> of(long page, long size) {
        return Page.of(page, size);
    }

    /**
     * <p>
     * 将 MyBatis-Plus {@link IPage} 转换为 {@link CocoPage}。
     * </p>
     * @param iPage MyBatis-Plus 分页结果
     * @param <T> 数据类型
     * @return Coco 通用分页结果
     */
    public static <T> CocoPage<T> toCocoPage(IPage<T> iPage) {
        Objects.requireNonNull(iPage, "iPage must not be null");
        return new CocoPage<>(iPage.getRecords(), iPage.getTotal(),
                iPage.getCurrent(), iPage.getSize());
    }

    static void applyOrders(Page<?> page, List<CocoSortOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<OrderItem> orderItems = orders.stream()
                .map(order -> order.ascending() ? OrderItem.asc(order.field()) : OrderItem.desc(order.field()))
                .toList();
        page.addOrder(orderItems);
    }
}
