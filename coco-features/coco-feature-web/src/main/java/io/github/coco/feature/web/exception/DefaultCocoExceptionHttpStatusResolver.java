package io.github.coco.feature.web.exception;

import java.util.Objects;

import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.common.exception.CocoException;
import io.github.coco.common.exception.type.CocoConflictException;
import io.github.coco.common.exception.type.CocoForbiddenException;
import io.github.coco.common.exception.type.CocoNotFoundException;
import io.github.coco.common.exception.type.CocoRequestException;
import io.github.coco.common.exception.type.CocoSystemException;
import io.github.coco.common.exception.type.CocoUnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 默认 Coco 异常 HTTP 状态解析器。
 * <p>
 * 根据 Coco 类型化异常返回对应 HTTP 状态；未细分的 {@link CocoException} 默认视为请求异常。
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
        CocoException checkedException = Objects.requireNonNull(exception, "exception must not be null");
        if (checkedException instanceof CocoPayloadTooLargeException) {
            return HttpStatusCode.valueOf(413);
        }
        if (checkedException instanceof CocoRequestException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (checkedException instanceof CocoUnauthorizedException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (checkedException instanceof CocoForbiddenException) {
            return HttpStatus.FORBIDDEN;
        }
        if (checkedException instanceof CocoNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (checkedException instanceof CocoConflictException) {
            return HttpStatus.CONFLICT;
        }
        if (checkedException instanceof CocoSystemException) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpStatusCode statusCode = resolveByMessageCode(checkedException.messageCode());
        if (statusCode != null) {
            return statusCode;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static HttpStatusCode resolveByMessageCode(String messageCode) {
        if (CocoCommonErrorCode.INVALID_ARGUMENT.code().equals(messageCode)
                || CocoCommonErrorCode.MISSING_MESSAGE_CODE.code().equals(messageCode)
                || CocoCommonErrorCode.MISSING_ERROR_CODE.code().equals(messageCode)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (CocoCommonErrorCode.UNAUTHORIZED.code().equals(messageCode)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (CocoCommonErrorCode.FORBIDDEN.code().equals(messageCode)) {
            return HttpStatus.FORBIDDEN;
        }
        if (CocoCommonErrorCode.NOT_FOUND.code().equals(messageCode)) {
            return HttpStatus.NOT_FOUND;
        }
        if (CocoCommonErrorCode.CONFLICT.code().equals(messageCode)) {
            return HttpStatus.CONFLICT;
        }
        if (CocoCommonErrorCode.UNKNOWN.code().equals(messageCode)
                || CocoCommonErrorCode.INTERNAL_ERROR.code().equals(messageCode)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return null;
    }
}
