package io.github.coco.feature.web.context;

/**
 * Coco Web 请求规范化文本。
 * <p>
 * 保存用于 Sign 签名的规范化请求文本以及对应摘要。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param text 规范化请求文本
 * @param sha256 规范化请求文本 SHA-256 摘要
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestCanonicalForm(String text, String sha256) {
}
