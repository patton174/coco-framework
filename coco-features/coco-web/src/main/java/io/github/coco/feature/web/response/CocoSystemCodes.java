package io.github.coco.feature.web.response;

import java.util.Objects;

import io.github.coco.exception.CocoBusinessCode;

/**
 * Coco Web 默认系统响应码。
 * <p>
 * 提供框架内置的系统响应码集合和 Builder，业务系统可以在配置类中用代码方式创建自定义系统码提供器。
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
public final class CocoSystemCodes implements CocoSystemCodeProvider {

    private static final CocoSystemCodes DEFAULTS = builder().build();

    private final int success;

    private final int invalidArgument;

    private final int unauthorized;

    private final int forbidden;

    private final int notFound;

    private final int conflict;

    private final int internalError;

    private CocoSystemCodes(Builder builder) {
        this.success = builder.success;
        this.invalidArgument = builder.invalidArgument;
        this.unauthorized = builder.unauthorized;
        this.forbidden = builder.forbidden;
        this.notFound = builder.notFound;
        this.conflict = builder.conflict;
        this.internalError = builder.internalError;
    }

    /**
     * <p>
     * 返回框架默认系统响应码提供器。
     * </p>
     * @return 默认系统响应码提供器
     */
    public static CocoSystemCodes defaults() {
        return DEFAULTS;
    }

    /**
     * <p>
     * 创建系统响应码 Builder。
     * </p>
     * @return 系统响应码 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int success() {
        return this.success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int invalidArgument() {
        return this.invalidArgument;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int unauthorized() {
        return this.unauthorized;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int forbidden() {
        return this.forbidden;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int notFound() {
        return this.notFound;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int conflict() {
        return this.conflict;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int internalError() {
        return this.internalError;
    }

    /**
     * Coco Web 系统响应码 Builder。
     * <p>
     * 默认值与常见 HTTP 语义保持一致：成功 200，请求错误 400，未认证 401，无权限 403，不存在 404，冲突 409，
     * 系统错误 500。
     * </p>
     */
    public static final class Builder {

        private int success = 200;

        private int invalidArgument = 400;

        private int unauthorized = 401;

        private int forbidden = 403;

        private int notFound = 404;

        private int conflict = 409;

        private int internalError = 500;

        private Builder() {
        }

        /**
         * <p>
         * 设置成功响应码。
         * </p>
         * @param code 响应码
         * @return 当前 Builder
         */
        public Builder success(int code) {
            this.success = code;
            return this;
        }

        /**
         * <p>
         * 使用业务码设置成功响应码。
         * </p>
         * @param code 业务码契约
         * @return 当前 Builder
         */
        public Builder success(CocoBusinessCode code) {
            return success(code(code));
        }

        /**
         * <p>
         * 设置请求参数错误响应码。
         * </p>
         * @param code 响应码
         * @return 当前 Builder
         */
        public Builder invalidArgument(int code) {
            this.invalidArgument = code;
            return this;
        }

        /**
         * <p>
         * 使用业务码设置请求参数错误响应码。
         * </p>
         * @param code 业务码契约
         * @return 当前 Builder
         */
        public Builder invalidArgument(CocoBusinessCode code) {
            return invalidArgument(code(code));
        }

        /**
         * <p>
         * 设置未认证响应码。
         * </p>
         * @param code 响应码
         * @return 当前 Builder
         */
        public Builder unauthorized(int code) {
            this.unauthorized = code;
            return this;
        }

        /**
         * <p>
         * 使用业务码设置未认证响应码。
         * </p>
         * @param code 业务码契约
         * @return 当前 Builder
         */
        public Builder unauthorized(CocoBusinessCode code) {
            return unauthorized(code(code));
        }

        /**
         * <p>
         * 设置无权限响应码。
         * </p>
         * @param code 响应码
         * @return 当前 Builder
         */
        public Builder forbidden(int code) {
            this.forbidden = code;
            return this;
        }

        /**
         * <p>
         * 使用业务码设置无权限响应码。
         * </p>
         * @param code 业务码契约
         * @return 当前 Builder
         */
        public Builder forbidden(CocoBusinessCode code) {
            return forbidden(code(code));
        }

        /**
         * <p>
         * 设置资源不存在响应码。
         * </p>
         * @param code 响应码
         * @return 当前 Builder
         */
        public Builder notFound(int code) {
            this.notFound = code;
            return this;
        }

        /**
         * <p>
         * 使用业务码设置资源不存在响应码。
         * </p>
         * @param code 业务码契约
         * @return 当前 Builder
         */
        public Builder notFound(CocoBusinessCode code) {
            return notFound(code(code));
        }

        /**
         * <p>
         * 设置资源冲突响应码。
         * </p>
         * @param code 响应码
         * @return 当前 Builder
         */
        public Builder conflict(int code) {
            this.conflict = code;
            return this;
        }

        /**
         * <p>
         * 使用业务码设置资源冲突响应码。
         * </p>
         * @param code 业务码契约
         * @return 当前 Builder
         */
        public Builder conflict(CocoBusinessCode code) {
            return conflict(code(code));
        }

        /**
         * <p>
         * 设置系统内部错误响应码。
         * </p>
         * @param code 响应码
         * @return 当前 Builder
         */
        public Builder internalError(int code) {
            this.internalError = code;
            return this;
        }

        /**
         * <p>
         * 使用业务码设置系统内部错误响应码。
         * </p>
         * @param code 业务码契约
         * @return 当前 Builder
         */
        public Builder internalError(CocoBusinessCode code) {
            return internalError(code(code));
        }

        /**
         * <p>
         * 创建不可变系统响应码提供器。
         * </p>
         * @return 系统响应码提供器
         */
        public CocoSystemCodes build() {
            return new CocoSystemCodes(this);
        }

        private static int code(CocoBusinessCode code) {
            return Objects.requireNonNull(code, "code must not be null").code();
        }
    }
}
