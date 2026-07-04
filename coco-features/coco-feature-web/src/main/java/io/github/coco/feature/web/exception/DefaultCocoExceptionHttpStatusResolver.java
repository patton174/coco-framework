package io.github.coco.feature.web.exception;

import java.util.Objects;

import io.github.coco.common.exception.CocoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 默认 Coco 异常 HTTP 状态解析器。
 * <p>
 * 第一阶段将 {@link CocoException} 统一视为请求异常并返回 {@link HttpStatus#BAD_REQUEST}，业务项目可通过自定义
 * {@link CocoExceptionHttpStatusResolver} 覆盖该策略。
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
public final class DefaultCocoExceptionHttpStatusResolver implements CocoExceptionHttpStatusResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpStatusCode resolve(CocoException exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return HttpStatus.BAD_REQUEST;
    }
}
