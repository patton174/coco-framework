package io.github.coco.feature.concurrencylimit;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Coco 并发限制拒绝响应写出器。
 */
@FunctionalInterface
public interface CocoConcurrencyLimitResponseWriter {

    /**
     * 写出 Coco 统一拒绝响应。
     * @param errorCode 并发限制错误码
     * @param request 当前请求
     * @param response 当前响应
     * @throws IOException 响应写出失败时抛出
     */
    void write(CocoConcurrencyLimitErrorCode errorCode, HttpServletRequest request,
            HttpServletResponse response) throws IOException;
}
