package io.github.coco.feature.httpclient;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

/**
 * 命名 HTTP 客户端收到非成功响应时抛出的异常。
 */
public final class CocoHttpClientException extends RuntimeException {

    private final String clientName;
    private final HttpMethod method;
    private final String uri;
    private final HttpStatusCode status;
    private final String responseSummary;

    /**
     * 创建 HTTP 客户端异常。
     * @param clientName 客户端名称
     * @param method 请求方法
     * @param uri 已脱敏 URI
     * @param status 响应状态
     * @param responseSummary 截断后的响应摘要
     */
    public CocoHttpClientException(String clientName, HttpMethod method, String uri, HttpStatusCode status,
            String responseSummary) {
        super("HTTP client '" + clientName + "' received " + status.value() + " for " + method + " " + uri);
        this.clientName = clientName;
        this.method = method;
        this.uri = uri;
        this.status = status;
        this.responseSummary = responseSummary;
    }

    public String getClientName() { return this.clientName; }
    public HttpMethod getMethod() { return this.method; }
    public String getUri() { return this.uri; }
    public HttpStatusCode getStatus() { return this.status; }
    public String getResponseSummary() { return this.responseSummary; }
}
