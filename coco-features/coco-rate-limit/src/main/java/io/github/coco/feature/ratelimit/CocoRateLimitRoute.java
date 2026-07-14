package io.github.coco.feature.ratelimit;

import io.github.coco.feature.web.context.CocoWebRequestMatchRule;

/**
 * 一条显式限流路由。
 */
public class CocoRateLimitRoute {

    private String id;

    private CocoWebRequestMatchRule matcher = new CocoWebRequestMatchRule();

    private long limit = 100;

    private long windowSeconds = 60;

    /**
     * 返回路由标识。
     * @return 路由标识
     */
    public String getId() {
        return this.id;
    }

    /**
     * 设置路由标识。
     * @param id 路由标识
     */
    public void setId(String id) {
        this.id = id == null ? null : id.trim();
    }

    /**
     * 返回 Web 匹配规则。
     * @return Web 匹配规则
     */
    public CocoWebRequestMatchRule getMatcher() {
        return this.matcher;
    }

    /**
     * 设置 Web 匹配规则。
     * @param matcher Web 匹配规则
     */
    public void setMatcher(CocoWebRequestMatchRule matcher) {
        this.matcher = matcher == null ? new CocoWebRequestMatchRule() : matcher;
    }

    /**
     * 返回窗口内允许的请求数。
     * @return 请求上限
     */
    public long getLimit() {
        return this.limit;
    }

    /**
     * 设置窗口内允许的请求数。
     * @param limit 请求上限
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }

    /**
     * 返回固定窗口时长秒数。
     * @return 窗口时长秒数
     */
    public long getWindowSeconds() {
        return this.windowSeconds;
    }

    /**
     * 设置固定窗口时长秒数。
     * @param windowSeconds 窗口时长秒数
     */
    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    boolean valid() {
        return this.id != null && !this.id.isBlank() && this.matcher != null && !this.matcher.isEmpty()
                && this.limit > 0 && this.windowSeconds > 0;
    }
}
