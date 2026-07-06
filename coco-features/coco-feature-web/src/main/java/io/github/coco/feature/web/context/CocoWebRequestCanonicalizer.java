package io.github.coco.feature.web.context;

/**
 * Coco Web 请求规范化器。
 * <p>
 * 将请求安全输入转换为稳定文本，供后续 Sign 签名和验签能力使用。
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
public interface CocoWebRequestCanonicalizer {

    /**
     * <p>
     * 创建指定用途下的规范化请求文本。
     * </p>
     * @param context 请求规范化上下文
     * @return 规范化请求文本
     */
    default CocoWebRequestCanonicalForm canonicalize(CocoWebRequestCanonicalizationContext context) {
        return canonicalize(context == null ? CocoWebRequestSecurityInput.empty() : context.securityInput());
    }

    /**
     * <p>
     * 创建规范化请求文本。
     * </p>
     * @param input 请求安全输入
     * @return 规范化请求文本
     */
    CocoWebRequestCanonicalForm canonicalize(CocoWebRequestSecurityInput input);
}
