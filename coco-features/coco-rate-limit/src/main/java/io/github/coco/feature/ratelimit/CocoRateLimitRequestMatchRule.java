package io.github.coco.feature.ratelimit;

import java.util.ArrayList;
import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * 限流路由的 Servlet 请求匹配规则。
 * <p>
 * 路径使用 Spring Ant 风格模式；空 methods 表示接受全部 HTTP 方法。规则属于限流模块，避免限流依赖
 * 具体 Web 功能实现。
 * </p>
 */
public final class CocoRateLimitRequestMatchRule {

    private final List<String> methods = new ArrayList<>();

    private final List<String> pathPatterns = new ArrayList<>();

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring configuration binding requires live lists; route matchers snapshot them at startup.")
    public List<String> getMethods() {
        return this.methods;
    }

    public void setMethods(List<String> methods) {
        replace(this.methods, methods);
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring configuration binding requires live lists; route matchers snapshot them at startup.")
    public List<String> getPathPatterns() {
        return this.pathPatterns;
    }

    public void setPathPatterns(List<String> pathPatterns) {
        replace(this.pathPatterns, pathPatterns);
    }

    boolean isEmpty() {
        return this.pathPatterns.stream().noneMatch(value -> value != null && !value.isBlank());
    }

    private static void replace(List<String> target, List<String> source) {
        target.clear();
        if (source != null) {
            source.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).forEach(target::add);
        }
    }
}
