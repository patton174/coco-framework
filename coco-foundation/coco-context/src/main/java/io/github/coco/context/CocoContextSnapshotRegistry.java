package io.github.coco.context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coco 上下文快照汇聚器。
 * <p>
 * 按稳定的登记顺序汇聚不同模块捕获的上下文快照。同一键再次登记时替换原快照但不改变顺序，适合一次请求中由多个基础设施模块共同组装异步回调上下文。
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
 * @since 1.0.0
 */
public final class CocoContextSnapshotRegistry {

    private final Map<String, CocoContextSnapshot> snapshots = new LinkedHashMap<>();

    /**
     * <p>
     * 登记或替换指定模块的上下文快照。
     * </p>
     * @param key 快照键
     * @param snapshot 上下文快照
     */
    public synchronized void register(String key, CocoContextSnapshot snapshot) {
        String checkedKey = Objects.requireNonNull(key, "key must not be null");
        if (checkedKey.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        this.snapshots.put(checkedKey, Objects.requireNonNull(snapshot, "snapshot must not be null"));
    }

    /**
     * <p>
     * 返回当前所有已登记快照的稳定组合。
     * </p>
     * @return 组合后的上下文快照
     */
    public synchronized CocoContextSnapshot snapshot() {
        return CocoContextSnapshot.compose(List.copyOf(this.snapshots.values()));
    }
}
