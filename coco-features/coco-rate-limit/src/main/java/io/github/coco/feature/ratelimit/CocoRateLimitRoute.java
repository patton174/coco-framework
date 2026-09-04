package io.github.coco.feature.ratelimit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * 一条显式限流路由。
 */
public class CocoRateLimitRoute {

    /** 最大支持 366 天的固定窗口。 */
    static final long MAX_WINDOW_SECONDS = 366L * 24 * 60 * 60;

    private String id;

    private final CocoRateLimitRequestMatchRule matcher = new CocoRateLimitRequestMatchRule();

    private long limit = 100;

    private long windowSeconds = 60;

    private CocoRateLimitAlgorithm algorithm = CocoRateLimitAlgorithm.FIXED_WINDOW;

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
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The established JavaBean API intentionally exposes a live matcher; runtime consumers take deep route snapshots.")
    public CocoRateLimitRequestMatchRule getMatcher() {
        return this.matcher;
    }

    /**
     * 设置 Web 匹配规则。
     * @param matcher Web 匹配规则
     */
    public void setMatcher(CocoRateLimitRequestMatchRule matcher) {
        CocoRateLimitRequestMatchRule copy = copyMatcher(matcher);
        this.matcher.setMethods(copy.getMethods());
        this.matcher.setPathPatterns(copy.getPathPatterns());
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

    /**
     * 返回限流算法。
     * @return 限流算法
     */
    public CocoRateLimitAlgorithm getAlgorithm() {
        return this.algorithm;
    }

    /**
     * 设置限流算法。
     * @param algorithm 限流算法；{@code null} 回退到固定窗口
     */
    public void setAlgorithm(CocoRateLimitAlgorithm algorithm) {
        this.algorithm = algorithm == null ? CocoRateLimitAlgorithm.FIXED_WINDOW : algorithm;
    }

    boolean valid() {
        return this.id != null && !this.id.isBlank() && this.matcher != null && !this.matcher.isEmpty()
                && this.limit > 0 && this.algorithm != null && isSupportedWindowSeconds(this.windowSeconds);
    }

    static boolean isSupportedWindowSeconds(long windowSeconds) {
        return windowSeconds > 0 && windowSeconds <= MAX_WINDOW_SECONDS;
    }

    static CocoRateLimitRoute copyOf(CocoRateLimitRoute source) {
        if (source == null) {
            return null;
        }
        CocoRateLimitRoute copy = new CocoRateLimitRoute();
        copy.setId(source.getId());
        copy.setMatcher(source.getMatcher());
        copy.setLimit(source.getLimit());
        copy.setWindowSeconds(source.getWindowSeconds());
        copy.setAlgorithm(source.getAlgorithm());
        return copy;
    }

    private static CocoRateLimitRequestMatchRule copyMatcher(CocoRateLimitRequestMatchRule source) {
        CocoRateLimitRequestMatchRule copy = new CocoRateLimitRequestMatchRule();
        if (source == null) {
            return copy;
        }
        copy.setMethods(source.getMethods());
        copy.setPathPatterns(source.getPathPatterns());
        return copy;
    }
}
