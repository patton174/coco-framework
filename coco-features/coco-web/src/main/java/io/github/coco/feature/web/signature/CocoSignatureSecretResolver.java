package io.github.coco.feature.web.signature;

import java.util.Optional;

/**
 * Coco 请求签名密钥解析器。
 * <p>
 * 业务项目可以提供同类型 Bean 覆盖默认配置解析策略，从数据库、配置中心或密钥管理服务读取签名密钥。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoSignatureSecretResolver {

    /**
     * <p>
     * 解析请求签名密钥。
     * </p>
     * @param request 请求签名材料
     * @return 请求签名密钥；未配置时为空
     */
    Optional<CocoSignatureSecret> resolve(CocoSignatureRequest request);
}
