package io.github.coco.feature.web.context;

import java.util.List;

/**
 * Coco Web 请求匹配器配置属性。
 * <p>
 * 为签名、加密、防重放等能力提供统一的 required 和 ignored 请求规则，避免每个能力重复实现路径判断。
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
 * @since 1.0.0
 */
public class CocoWebRequestMatcherProperties {

    private List<CocoWebRequestMatchRule> required = List.of();

    private List<CocoWebRequestMatchRule> ignored = List.of();

    /**
     * <p>
     * 返回需要强制启用当前能力的请求规则。
     * </p>
     * @return 强制启用规则
     */
    public List<CocoWebRequestMatchRule> getRequired() {
        return this.required;
    }

    /**
     * <p>
     * 设置需要强制启用当前能力的请求规则。
     * </p>
     * @param required 强制启用规则
     */
    public void setRequired(List<CocoWebRequestMatchRule> required) {
        this.required = normalizeRules(required);
    }

    /**
     * <p>
     * 返回需要忽略当前能力的请求规则。
     * </p>
     * @return 忽略规则
     */
    public List<CocoWebRequestMatchRule> getIgnored() {
        return this.ignored;
    }

    /**
     * <p>
     * 设置需要忽略当前能力的请求规则。
     * </p>
     * @param ignored 忽略规则
     */
    public void setIgnored(List<CocoWebRequestMatchRule> ignored) {
        this.ignored = normalizeRules(ignored);
    }

    private static List<CocoWebRequestMatchRule> normalizeRules(List<CocoWebRequestMatchRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<CocoWebRequestMatchRule> normalized = rules.stream()
                .filter(rule -> rule != null && !rule.isEmpty())
                .toList();
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }
}
