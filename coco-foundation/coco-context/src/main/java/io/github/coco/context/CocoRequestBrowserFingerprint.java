package io.github.coco.context;

import java.util.Map;
import java.util.Optional;

/**
 * 浏览器指纹视图。
 * <p>
 * 提供浏览器指纹值和指纹信号的结构化访问，
 * 属性来源于 {@link CocoRequestContext} 的内部属性快照。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public final class CocoRequestBrowserFingerprint {

    private final Map<String, String> attributes;

    CocoRequestBrowserFingerprint(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * <p>
     * 返回浏览器指纹。
     * </p>
     * @return 浏览器指纹；未设置时为空
     */
    public Optional<String> value() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.BROWSER_FINGERPRINT);
    }

    /**
     * <p>
     * 返回浏览器指纹信号快照。
     * </p>
     * @return 浏览器指纹信号快照
     */
    public Map<String, String> signals() {
        return CocoRequestContextAttributeParser.prefixedAttributes(this.attributes,
                CocoRequestContextAttributes.BROWSER_FINGERPRINT_SIGNAL_PREFIX);
    }

    /**
     * <p>
     * 返回指定浏览器指纹信号。
     * </p>
     * @param name 浏览器指纹信号名称
     * @return 浏览器指纹信号值；未设置时为空
     */
    public Optional<String> signal(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.browserFingerprintSignal(name));
    }
}
