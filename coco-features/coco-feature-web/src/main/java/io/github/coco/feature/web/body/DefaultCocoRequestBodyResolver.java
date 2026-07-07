package io.github.coco.feature.web.body;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 请求体解析器。
 * <p>
 * 从 {@link CocoCachedBodyHttpServletRequest} 暴露的缓存属性中解析传输态和业务态请求体，不主动消费原始输入流。
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
public final class DefaultCocoRequestBodyResolver implements CocoRequestBodyResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoResolvedRequestBody resolve(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        CocoCachedRequestBody effectiveBody = CocoCachedBodyHttpServletRequest.effectiveBody(checkedRequest)
                .orElse(CocoCachedRequestBody.empty());
        CocoCachedRequestBody transportBody = CocoCachedBodyHttpServletRequest.transportBody(checkedRequest)
                .orElse(effectiveBody);
        return new CocoResolvedRequestBody(transportBody, effectiveBody,
                CocoRequestBodyMetadata.from(transportBody, effectiveBody), checkedRequest.getContentType(),
                checkedRequest.getCharacterEncoding());
    }
}
