package io.github.coco.feature.idempotency;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 写出幂等拒绝响应的可替换 SPI。 */
@FunctionalInterface
public interface CocoIdempotencyResponseWriter {
    /** 写出统一拒绝响应。 */
    void write(CocoIdempotencyErrorCode errorCode, HttpServletRequest request, HttpServletResponse response) throws IOException;
}
