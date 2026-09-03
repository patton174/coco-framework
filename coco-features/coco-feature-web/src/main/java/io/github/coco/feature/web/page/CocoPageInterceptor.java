package io.github.coco.feature.web.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.coco.context.CocoSortOrder;
import io.github.coco.context.CocoPageContext;
import io.github.coco.context.CocoPageContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Coco 分页参数拦截器。
 * <p>
 * 在请求入口从查询参数中解析页码、每页大小和排序字段，校验边界后写入
 * {@link CocoPageContextHolder}，请求完成后自动清除上下文，防止线程池复用导致的数据泄漏。
 * </p>
 * <p>
 * 排序参数格式为 {@code ?sort=field,asc&sort=field2,desc}，字段名仅允许字母、数字和下划线。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public class CocoPageInterceptor implements HandlerInterceptor {

    private static final Pattern SAFE_FIELD_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private final CocoPageProperties properties;

    CocoPageInterceptor(CocoPageProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long page = parseLong(request.getParameter(this.properties.getPageParameterName()),
                this.properties.getDefaultPage());
        long size = parseLong(request.getParameter(this.properties.getSizeParameterName()),
                this.properties.getDefaultSize());
        if (page < 1) {
            page = this.properties.getDefaultPage();
        }
        if (size < 1) {
            size = this.properties.getDefaultSize();
        }
        if (size > this.properties.getMaxSize()) {
            size = this.properties.getMaxSize();
        }
        List<CocoSortOrder> orders = parseSortOrders(
                request.getParameterValues(this.properties.getSortParameterName()));
        CocoPageContextHolder.set(new CocoPageContext(page, size, orders));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        CocoPageContextHolder.clear();
    }

    private static List<CocoSortOrder> parseSortOrders(String[] sortValues) {
        if (sortValues == null || sortValues.length == 0) {
            return List.of();
        }
        List<CocoSortOrder> orders = new ArrayList<>();
        for (String sortValue : sortValues) {
            if (sortValue == null || sortValue.isBlank()) {
                continue;
            }
            String[] parts = sortValue.split(",", 2);
            String field = parts[0].trim();
            if (!SAFE_FIELD_PATTERN.matcher(field).matches()) {
                continue;
            }
            boolean ascending = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1].trim());
            orders.add(new CocoSortOrder(field, ascending));
        }
        return orders.isEmpty() ? List.of() : List.copyOf(orders);
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        }
        catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
