package io.github.coco.feature.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Coco Web 统一响应模型。
 * <p>
 * 用于承载 Web 层返回给调用方的稳定响应结构。响应体默认只输出业务语义字段，链路元数据仅在显式配置后输出。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param success 请求是否成功
 * @param code 响应编码
 * @param message 响应消息
 * @param data 响应数据
 * @param traceId 请求链路标识；未配置响应体元数据时不会序列化
 * @param path 请求路径；仅调试或兼容模式输出
 * @param <T> 响应数据类型
 * @author patton174
 * @since 1.0.0
 */
public record CocoApiResponse<T>(boolean success, int code, String message, T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String path) {

    /**
     * <p>
     * 创建统一响应对象，并校验响应编码。
     * </p>
     * @param success 请求是否成功
     * @param code 响应码
     * @param message 响应消息
     * @param data 响应数据
     * @param traceId 请求链路标识
     * @param path 请求路径
     */
    public CocoApiResponse {
        message = message == null ? "" : message;
        traceId = blankToNull(traceId);
        path = blankToNull(path);
    }

    /**
     * <p>
     * 创建异常响应对象。
     * </p>
     * @param code 异常响应码
     * @param message 异常消息
     * @param <T> 响应数据类型
     * @return 统一异常响应
     */
    public static <T> CocoApiResponse<T> error(int code, String message) {
        return error(code, message, CocoResponseMetadata.empty());
    }

    /**
     * <p>
     * 创建异常响应对象。
     * </p>
     * @param code 异常响应码
     * @param message 异常消息
     * @param metadata 响应元数据
     * @param <T> 响应数据类型
     * @return 统一异常响应
     */
    public static <T> CocoApiResponse<T> error(int code, String message, CocoResponseMetadata metadata) {
        CocoResponseMetadata checkedMetadata = metadata == null ? CocoResponseMetadata.empty() : metadata;
        return error(code, message, checkedMetadata.traceId(), checkedMetadata.path());
    }

    /**
     * <p>
     * 创建异常响应对象。
     * </p>
     * @param code 异常响应码
     * @param message 异常消息
     * @param traceId 请求链路标识
     * @param path 请求路径
     * @param <T> 响应数据类型
     * @return 统一异常响应
     */
    public static <T> CocoApiResponse<T> error(int code, String message, String traceId, String path) {
        return new CocoApiResponse<>(false, code, message, null, traceId, path);
    }

    /**
     * <p>
     * 创建成功响应对象。
     * </p>
     * @param code 成功响应码
     * @param message 成功响应消息
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> CocoApiResponse<T> success(int code, String message, T data) {
        return success(code, message, data, CocoResponseMetadata.empty());
    }

    /**
     * <p>
     * 创建成功响应对象。
     * </p>
     * @param code 成功响应码
     * @param message 成功响应消息
     * @param data 响应数据
     * @param metadata 响应元数据
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> CocoApiResponse<T> success(int code, String message, T data, CocoResponseMetadata metadata) {
        CocoResponseMetadata checkedMetadata = metadata == null ? CocoResponseMetadata.empty() : metadata;
        return success(code, message, data, checkedMetadata.traceId(), checkedMetadata.path());
    }

    /**
     * <p>
     * 创建成功响应对象。
     * </p>
     * @param code 成功响应码
     * @param message 成功响应消息
     * @param data 响应数据
     * @param traceId 请求链路标识
     * @param path 请求路径
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> CocoApiResponse<T> success(int code, String message, T data, String traceId, String path) {
        return new CocoApiResponse<>(true, code, message, data, traceId, path);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
