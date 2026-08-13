package io.github.coco.feature.concurrencylimit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 在途请求并发限制配置属性。
 */
@ConfigurationProperties("coco.concurrency-limit")
public class CocoConcurrencyLimitProperties {

    private boolean enabled;

    private int globalLimit;

    private CocoConcurrencyLimitAsyncPolicy asyncPolicy = CocoConcurrencyLimitAsyncPolicy.TRACK;

    private List<CocoConcurrencyLimitRoute> routes = new ArrayList<>();

    private InMemory inMemory = new InMemory();

    private Response response = new Response();

    /**
     * 返回模块是否启用。
     * @return 启用状态
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置模块是否启用。
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回受保护请求的全局进程内并发上限。
     * @return 全局并发上限；零表示禁用该维度
     */
    public int getGlobalLimit() {
        return this.globalLimit;
    }

    /**
     * 设置受保护请求的全局进程内并发上限。
     * @param globalLimit 全局并发上限
     */
    public void setGlobalLimit(int globalLimit) {
        this.globalLimit = globalLimit;
    }

    /**
     * 返回 Servlet 异步请求策略。
     * @return 异步请求策略
     */
    public CocoConcurrencyLimitAsyncPolicy getAsyncPolicy() {
        return this.asyncPolicy;
    }

    /**
     * 设置 Servlet 异步请求策略。
     * @param asyncPolicy 异步请求策略
     */
    public void setAsyncPolicy(CocoConcurrencyLimitAsyncPolicy asyncPolicy) {
        this.asyncPolicy = asyncPolicy == null ? CocoConcurrencyLimitAsyncPolicy.TRACK : asyncPolicy;
    }

    /**
     * 返回并发限制路由。
     * @return 路由列表
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Spring Boot binds the mutable configuration collection through this getter")
    public List<CocoConcurrencyLimitRoute> getRoutes() {
        return this.routes;
    }

    /**
     * 设置并发限制路由。
     * @param routes 路由列表
     */
    public void setRoutes(List<CocoConcurrencyLimitRoute> routes) {
        this.routes = routes == null ? new ArrayList<>() : new ArrayList<>(routes);
    }

    /**
     * 返回进程内存储配置。
     * @return 进程内存储配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Spring Boot binds nested mutable configuration through this getter")
    public InMemory getInMemory() {
        return this.inMemory;
    }

    /**
     * 设置进程内存储配置。
     * @param inMemory 进程内存储配置
     */
    public void setInMemory(InMemory inMemory) {
        this.inMemory = inMemory == null ? new InMemory() : inMemory;
    }

    /**
     * 返回拒绝响应配置。
     * @return 拒绝响应配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Spring Boot binds nested mutable configuration through this getter")
    public Response getResponse() {
        return this.response;
    }

    /**
     * 设置拒绝响应配置。
     * @param response 拒绝响应配置
     */
    public void setResponse(Response response) {
        this.response = response == null ? new Response() : response;
    }

    /**
     * 进程内存储配置。
     */
    public static class InMemory {

        private int maxEntries = 10_000;

        /**
         * 返回活动计数键最大数量。
         * @return 活动键容量
         */
        public int getMaxEntries() {
            return this.maxEntries;
        }

        /**
         * 设置活动计数键最大数量。
         * @param maxEntries 活动键容量
         */
        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }

    /**
     * 拒绝响应配置。
     */
    public static class Response {

        private int status = 429;

        private int retryAfterSeconds = 1;

        private boolean includeHeaders = true;

        private Map<String, String> headers = new LinkedHashMap<>();

        /**
         * 返回拒绝响应 HTTP 状态码。
         * @return HTTP 状态码
         */
        public int getStatus() {
            return this.status;
        }

        /**
         * 设置拒绝响应 HTTP 状态码。
         * @param status HTTP 状态码
         */
        public void setStatus(int status) {
            this.status = status;
        }

        /**
         * 返回拒绝响应的重试等待秒数。
         * @return 重试等待秒数
         */
        public int getRetryAfterSeconds() {
            return this.retryAfterSeconds;
        }

        /**
         * 设置拒绝响应的重试等待秒数。
         * @param retryAfterSeconds 重试等待秒数
         */
        public void setRetryAfterSeconds(int retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
        }

        /**
         * 返回是否写出并发容量响应头。
         * @return 是否写出容量响应头
         */
        public boolean isIncludeHeaders() {
            return this.includeHeaders;
        }

        /**
         * 设置是否写出并发容量响应头。
         * @param includeHeaders 是否写出容量响应头
         */
        public void setIncludeHeaders(boolean includeHeaders) {
            this.includeHeaders = includeHeaders;
        }

        /**
         * 返回拒绝响应附加头。
         * @return 附加响应头
         */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP",
                justification = "Spring Boot binds the mutable configuration map through this getter")
        public Map<String, String> getHeaders() {
            return this.headers;
        }

        /**
         * 设置拒绝响应附加头。
         * @param headers 附加响应头
         */
        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        }
    }
}
