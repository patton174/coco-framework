package io.github.coco.feature.web.replay;

import io.github.coco.feature.web.security.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;

/**
 * Coco Web 防重放键解析器。
 * <p>
 * 业务项目可以提供同类型 Bean 覆盖默认策略，自定义重放判定维度。
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
@FunctionalInterface
public interface CocoReplayKeyResolver {

    /**
     * <p>
     * 解析当前请求的防重放键。
     * </p>
     * @param snapshot Web 请求快照
     * @param metadata 请求安全元数据
     * @return 防重放键
     */
    CocoReplayKey resolve(CocoWebRequestSnapshot snapshot, CocoWebRequestSecurityMetadata metadata);
}
