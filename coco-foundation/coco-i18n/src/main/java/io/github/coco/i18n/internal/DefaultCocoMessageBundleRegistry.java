package io.github.coco.i18n.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.coco.i18n.CocoMessageBundleRegistry;

/**
 * 默认 Coco 消息资源包注册表。
 * <p>
 * 以插入顺序保存 basename，同时去除重复项，确保消息资源解析顺序稳定。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoMessageBundleRegistry implements CocoMessageBundleRegistry {

    private final Set<String> basenames = new LinkedHashSet<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(String basename) {
        if (basename == null || basename.isBlank()) {
            throw new IllegalArgumentException("Message bundle basename must not be blank");
        }
        this.basenames.add(basename.trim());
    }

    /**
     * <p>
     * 返回已经注册的消息资源 basename 列表。
     * </p>
     * @return 消息资源 basename 列表
     */
    public List<String> basenames() {
        return List.copyOf(this.basenames);
    }
}
