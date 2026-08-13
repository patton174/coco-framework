package io.github.coco.feature.httpclient;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 非成功 HTTP 响应异常映射器。
 */
@FunctionalInterface
public interface CocoHttpErrorMapper {

    /**
     * 映射非 2xx 响应。
     * @param clientName 客户端名称
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @return 要抛出的运行时异常
     * @throws IOException 读取响应失败时抛出
     */
    RuntimeException map(String clientName, HttpRequest request, ClientHttpResponse response) throws IOException;
}
