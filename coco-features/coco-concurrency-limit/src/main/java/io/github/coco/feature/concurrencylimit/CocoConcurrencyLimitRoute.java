package io.github.coco.feature.concurrencylimit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.coco.feature.web.context.CocoWebRequestMatchRule;

/**
 * Coco 在途请求并发限制路由。
 */
public class CocoConcurrencyLimitRoute {

    private String id;

    private int order;

    private CocoWebRequestMatchRule matcher = new CocoWebRequestMatchRule();

    private int limit;

    private int keyLimit;

    /**
     * 返回路由唯一标识。
     * @return 路由标识
     */
    public String getId() {
        return this.id;
    }

    /**
     * 设置路由唯一标识。
     * @param id 路由标识
     */
    public void setId(String id) {
        this.id = normalize(id);
    }

    /**
     * 返回路由匹配顺序。
     * @return 路由顺序
     */
    public int getOrder() {
        return this.order;
    }

    /**
     * 设置路由匹配顺序。
     * @param order 路由顺序
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * 返回 Servlet 路由匹配规则。
     * @return Web 请求匹配规则
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Spring Boot binds the nested mutable route matcher through this getter")
    public CocoWebRequestMatchRule getMatcher() {
        return this.matcher;
    }

    /**
     * 设置 Servlet 路由匹配规则。
     * @param matcher Web 请求匹配规则
     */
    public void setMatcher(CocoWebRequestMatchRule matcher) {
        this.matcher = matcher == null ? new CocoWebRequestMatchRule() : matcher;
    }

    /**
     * 返回当前路由的并发上限。
     * @return 路由并发上限；零表示禁用该维度
     */
    public int getLimit() {
        return this.limit;
    }

    /**
     * 设置当前路由的并发上限。
     * @param limit 路由并发上限
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * 返回当前路由内每个解析键的并发上限。
     * @return 解析键并发上限；零表示禁用该维度
     */
    public int getKeyLimit() {
        return this.keyLimit;
    }

    /**
     * 设置当前路由内每个解析键的并发上限。
     * @param keyLimit 解析键并发上限
     */
    public void setKeyLimit(int keyLimit) {
        this.keyLimit = keyLimit;
    }

    boolean valid(int globalLimit) {
        return this.id != null && this.limit >= 0 && this.keyLimit >= 0
                && (globalLimit > 0 || this.limit > 0 || this.keyLimit > 0);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
