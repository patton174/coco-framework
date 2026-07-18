package io.github.coco.feature.web.context;

import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;

/**
 * Coco Web 请求规范化上下文�? * <p>
 * 汇聚请求规范化所需的稳定输入，�?Sign、AES、防重放和浏览器指纹等能力可以基于同一份上下文扩展各自协议�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param purpose 规范化用�? * @param securityInput 请求安全输入
 * @param securityMetadata 请求安全元数�? * @param browserFingerprint 浏览器指�? * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestCanonicalizationContext(CocoWebRequestCanonicalizationPurpose purpose,
        CocoWebRequestSecurityInput securityInput, CocoWebRequestSecurityMetadata securityMetadata,
        CocoBrowserFingerprint browserFingerprint) {

    /**
     * <p>
     * 创建请求规范化上下文，并归一化空对象�?     * </p>
     * @param purpose 规范化用�?     * @param securityInput 请求安全输入
     * @param securityMetadata 请求安全元数�?     * @param browserFingerprint 浏览器指�?     */
    public CocoWebRequestCanonicalizationContext {
        purpose = purpose == null ? CocoWebRequestCanonicalizationPurpose.GENERAL : purpose;
        securityInput = securityInput == null ? CocoWebRequestSecurityInput.empty() : securityInput;
        securityMetadata = securityMetadata == null ? CocoWebRequestSecurityMetadata.empty() : securityMetadata;
        browserFingerprint = browserFingerprint == null ? CocoBrowserFingerprint.empty() : browserFingerprint;
    }

    /**
     * <p>
     * 创建通用请求规范化上下文�?     * </p>
     * @param securityInput 请求安全输入
     * @return 请求规范化上下文
     */
    public static CocoWebRequestCanonicalizationContext of(CocoWebRequestSecurityInput securityInput) {
        return new CocoWebRequestCanonicalizationContext(CocoWebRequestCanonicalizationPurpose.GENERAL,
                securityInput, null, CocoBrowserFingerprint.empty());
    }

    /**
     * <p>
     * 创建指定用途的请求规范化上下文�?     * </p>
     * @param purpose 规范化用�?     * @param snapshot Web 请求快照
     * @param securityMetadata 请求安全元数�?     * @return 请求规范化上下文
     */
    public static CocoWebRequestCanonicalizationContext of(CocoWebRequestCanonicalizationPurpose purpose,
            CocoWebRequestSnapshot snapshot, CocoWebRequestSecurityMetadata securityMetadata) {
        if (snapshot == null) {
            return new CocoWebRequestCanonicalizationContext(purpose, CocoWebRequestSecurityInput.empty(),
                    securityMetadata, CocoBrowserFingerprint.empty());
        }
        return new CocoWebRequestCanonicalizationContext(purpose, snapshot.securityInput(),
                securityMetadata, snapshot.browserFingerprint());
    }
}
