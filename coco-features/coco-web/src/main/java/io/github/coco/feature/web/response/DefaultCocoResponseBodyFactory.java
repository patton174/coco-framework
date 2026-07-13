package io.github.coco.feature.web.response;

import java.util.Objects;

/**
 * Coco Web 默认响应体工厂。
 * <p>
 * 将响应语义负载转换为框架默认的 {@link CocoApiResponse} 输出结构。
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
public final class DefaultCocoResponseBodyFactory implements CocoResponseBodyFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public Object success(CocoResponsePayload<?> payload) {
        CocoResponsePayload<?> checkedPayload = requirePayload(payload);
        return CocoApiResponse.success(checkedPayload.code(), checkedPayload.message(),
                checkedPayload.data(), checkedPayload.metadata());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object error(CocoResponsePayload<?> payload) {
        CocoResponsePayload<?> checkedPayload = requirePayload(payload);
        return CocoApiResponse.error(checkedPayload.code(), checkedPayload.message(), checkedPayload.metadata());
    }

    private static CocoResponsePayload<?> requirePayload(CocoResponsePayload<?> payload) {
        return Objects.requireNonNull(payload, "payload must not be null");
    }
}
