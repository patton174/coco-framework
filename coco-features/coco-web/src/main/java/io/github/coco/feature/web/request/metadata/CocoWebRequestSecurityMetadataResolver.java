package io.github.coco.feature.web.request.metadata;

/**
 * Coco Web 请求安全元数据解析器�? * <p>
 * 业务项目可以提供同类�?Bean 覆盖默认策略，统一接管 Sign、AES 和后续防重放能力所需的协议材料解析�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoWebRequestSecurityMetadataResolver {

    /**
     * <p>
     * 从请求安全输入中解析安全元数据�?     * </p>
     * @param input 请求安全输入
     * @return 请求安全元数�?     */
    CocoWebRequestSecurityMetadata resolve(CocoWebRequestSecurityInput input);
}
