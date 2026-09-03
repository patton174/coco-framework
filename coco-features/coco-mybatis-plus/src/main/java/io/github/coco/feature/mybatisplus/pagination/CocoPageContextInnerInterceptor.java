package io.github.coco.feature.mybatisplus.pagination;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.ParameterUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.coco.context.CocoPageContext;
import io.github.coco.context.CocoPageContextHolder;
import io.github.coco.context.CocoSortOrder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * Coco 分页上下文自动注入拦截器。
 * <p>
 * 在查询执行前检查 Mapper 方法参数中的 {@link IPage} 对象，若当前线程存在
 * {@link CocoPageContextHolder} 上下文，则自动将分页和排序参数填充到 {@code IPage} 中，
 * 使 Repository 层无需手动构造分页参数。
 * </p>
 * <p>
 * 排序字段会经过 {@link CocoSortable} 白名单校验：仅 Entity 上标记了 {@code @CocoSortable}
 * 的字段允许参与排序，未命中白名单的排序字段被静默忽略。若 Entity 上没有任何 {@code @CocoSortable}
 * 注解，则所有排序请求都被忽略。
 * </p>
 * <p>
 * 该拦截器应注册在 {@code PaginationInnerInterceptor} 之前执行，确保分页参数在 SQL 重写前已就绪。
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
public class CocoPageContextInnerInterceptor implements InnerInterceptor {

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        Optional<CocoPageContext> contextOpt = CocoPageContextHolder.current();
        if (contextOpt.isEmpty()) {
            return;
        }
        Optional<IPage> pageOpt = ParameterUtils.findPage(parameter);
        if (pageOpt.isEmpty()) {
            return;
        }
        CocoPageContext context = contextOpt.get();
        IPage<?> page = pageOpt.get();
        page.setCurrent(context.page());
        page.setSize(context.size());
        if (!context.orders().isEmpty() && page instanceof Page<?> mpPage) {
            Class<?> entityClass = resolveEntityClass(ms);
            List<OrderItem> filtered = filterAndMapOrders(context.orders(), entityClass);
            if (!filtered.isEmpty()) {
                mpPage.addOrder(filtered);
            }
        }
    }

    private static Class<?> resolveEntityClass(MappedStatement ms) {
        List<ResultMap> resultMaps = ms.getResultMaps();
        if (resultMaps == null || resultMaps.isEmpty()) {
            return null;
        }
        return resultMaps.get(0).getType();
    }

    private static List<OrderItem> filterAndMapOrders(List<CocoSortOrder> orders, Class<?> entityClass) {
        Map<String, String> whitelist = CocoSortableFieldResolver.resolve(entityClass);
        if (whitelist.isEmpty()) {
            return List.of();
        }
        List<OrderItem> result = new ArrayList<>();
        for (CocoSortOrder order : orders) {
            String columnName = whitelist.get(order.field());
            if (columnName != null) {
                result.add(order.ascending() ? OrderItem.asc(columnName) : OrderItem.desc(columnName));
            }
        }
        return result;
    }
}
