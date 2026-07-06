package io.github.coco.common.logging.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coco 日志句柄注册表。
 * <p>
 * 保存框架内部模块注册的日志句柄，为日志管理器提供统一的句柄查询入口。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoLogHandleRegistry {

    private final Map<String, CocoLogHandle> handles = new LinkedHashMap<>();

    /**
     * <p>
     * 注册日志句柄。
     * </p>
     * @param handle 日志句柄
     */
    public void register(CocoLogHandle handle) {
        CocoLogHandle checkedHandle = Objects.requireNonNull(handle, "handle must not be null");
        this.handles.put(checkedHandle.name(), checkedHandle);
    }

    /**
     * <p>
     * 按名称查找日志句柄。
     * </p>
     * @param name 句柄名称
     * @return 日志句柄；不存在时为空
     */
    public Optional<CocoLogHandle> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.handles.get(name.trim()));
    }

    /**
     * <p>
     * 返回已注册句柄快照。
     * </p>
     * @return 已注册句柄集合
     */
    public Collection<CocoLogHandle> handles() {
        return java.util.List.copyOf(this.handles.values());
    }
}
