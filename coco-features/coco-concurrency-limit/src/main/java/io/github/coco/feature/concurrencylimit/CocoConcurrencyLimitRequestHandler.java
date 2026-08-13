package io.github.coco.feature.concurrencylimit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter 和 MVC 注解后备拦截器共用的并发许可请求执行器。
 */
public final class CocoConcurrencyLimitRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoConcurrencyLimitRequestHandler.class);

    private static final String GLOBAL_KEY = "global";

    private static final String LIMIT_HEADER_PREFIX = "X-Concurrency-Limit-";

    private static final String REMAINING_HEADER_PREFIX = "X-Concurrency-Remaining-";

    private final CocoConcurrencyLimitProperties properties;

    private final CocoConcurrencyLimitKeyResolver keyResolver;

    private final CocoConcurrencyLimitStore store;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoConcurrencyLimitResponseWriter responseWriter;

    /**
     * 创建并发许可请求执行器。
     * @param properties 并发限制配置
     * @param keyResolver 解析键 SPI
     * @param store 原子存储 SPI
     * @param requestContextResolver Coco Web 请求上下文解析器
     * @param responseWriter 拒绝响应写出器
     */
    public CocoConcurrencyLimitRequestHandler(CocoConcurrencyLimitProperties properties,
            CocoConcurrencyLimitKeyResolver keyResolver, CocoConcurrencyLimitStore store,
            CocoWebRequestContextResolver requestContextResolver,
            CocoConcurrencyLimitResponseWriter responseWriter) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    }

    /**
     * 尝试原子申请指定路由需要的全部并发许可。
     * @param route 已解析路由
     * @param request 当前请求
     * @param response 当前响应
     * @return 成功时携带 release-once 许可的生命周期句柄
     * @throws IOException 拒绝响应写出失败时抛出
     */
    public CocoConcurrencyLimitHandle acquire(CocoConcurrencyLimitRoute route, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        CocoConcurrencyLimitRoute checkedRoute = Objects.requireNonNull(route, "route must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");
        String traceId = CocoTraceContext.currentTraceId().orElseGet(CocoTraceContext::getOrCreateTraceId);
        CocoConcurrencyLimitAcquisition acquisition = null;
        boolean handedOff = false;
        try {
            CocoConcurrencyLimitRequest limitRequest = resolveRequest(checkedRoute, request, traceId);
            acquisition = this.store.acquire(limitRequest);
            writeCapacityHeaders(response, acquisition);
            if (!acquisition.acquired()) {
                writeRejectionHeaders(response, acquisition.rejectedDimension());
                CocoConcurrencyLimitErrorCode errorCode = acquisition.rejectionReason()
                        == CocoConcurrencyLimitRejectionReason.UNAVAILABLE
                        ? CocoConcurrencyLimitErrorCode.UNAVAILABLE : CocoConcurrencyLimitErrorCode.REJECTED;
                LOGGER.info("Coco concurrency-limit rejected route={} dimension={} reason={} traceId={}",
                        checkedRoute.getId(), acquisition.rejectedDimension(), acquisition.rejectionReason(), traceId);
                this.responseWriter.write(errorCode, request, response);
                return CocoConcurrencyLimitHandle.rejected();
            }
            CocoConcurrencyLimitHandle handle = CocoConcurrencyLimitHandle.acquired(this.store,
                    acquisition.permit());
            handedOff = true;
            return handle;
        }
        catch (RuntimeException exception) {
            LOGGER.warn("Coco concurrency-limit failed closed for route={} traceId={}", checkedRoute.getId(), traceId,
                    exception);
            writeRejectionHeaders(response, null);
            this.responseWriter.write(CocoConcurrencyLimitErrorCode.UNAVAILABLE, request, response);
            return CocoConcurrencyLimitHandle.rejected();
        }
        finally {
            if (acquisition != null && acquisition.acquired() && !handedOff) {
                this.store.release(acquisition.permit());
            }
        }
    }

    boolean handleAsyncDispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (this.properties.getAsyncPolicy() != CocoConcurrencyLimitAsyncPolicy.REJECT) {
            return true;
        }
        return rejectAsyncDispatch(request, response);
    }

    boolean rejectAsyncDispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeRejectionHeaders(response, null);
        this.responseWriter.write(CocoConcurrencyLimitErrorCode.ASYNC_REJECTED, request, response);
        return false;
    }

    CocoConcurrencyLimitAsyncPolicy asyncPolicy() {
        return this.properties.getAsyncPolicy();
    }

    private CocoConcurrencyLimitRequest resolveRequest(CocoConcurrencyLimitRoute route,
            HttpServletRequest request, String traceId) {
        List<CocoConcurrencyLimitConstraint> constraints = new ArrayList<>(3);
        if (this.properties.getGlobalLimit() > 0) {
            constraints.add(new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.GLOBAL,
                    GLOBAL_KEY, this.properties.getGlobalLimit()));
        }
        if (route.getLimit() > 0) {
            constraints.add(new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.ROUTE,
                    route.getId(), route.getLimit()));
        }
        if (route.getKeyLimit() > 0) {
            CocoWebRequestSnapshot snapshot = this.requestContextResolver.resolve(traceId, request);
            String resolvedKey = this.keyResolver.resolve(snapshot, route);
            if (resolvedKey == null || resolvedKey.isBlank()) {
                throw new IllegalStateException("CocoConcurrencyLimitKeyResolver returned a blank key");
            }
            constraints.add(new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.KEY,
                    route.getId() + '\0' + resolvedKey.trim(), route.getKeyLimit()));
        }
        return new CocoConcurrencyLimitRequest(constraints);
    }

    private void writeCapacityHeaders(HttpServletResponse response, CocoConcurrencyLimitAcquisition acquisition) {
        if (!this.properties.getResponse().isIncludeHeaders()) {
            return;
        }
        for (CocoConcurrencyLimitSnapshot snapshot : acquisition.snapshots()) {
            String suffix = headerSuffix(snapshot.dimension());
            response.setHeader(LIMIT_HEADER_PREFIX + suffix, Integer.toString(snapshot.limit()));
            response.setHeader(REMAINING_HEADER_PREFIX + suffix, Integer.toString(snapshot.remaining()));
        }
    }

    private void writeRejectionHeaders(HttpServletResponse response,
            CocoConcurrencyLimitDimension rejectedDimension) {
        CocoConcurrencyLimitProperties.Response responseProperties = this.properties.getResponse();
        for (Map.Entry<String, String> header : responseProperties.getHeaders().entrySet()) {
            if (header.getKey() != null && !header.getKey().isBlank()
                    && header.getValue() != null && !header.getValue().isBlank()) {
                response.setHeader(header.getKey().trim(), header.getValue().trim());
            }
        }
        if (responseProperties.isIncludeHeaders()) {
            if (rejectedDimension != null) {
                response.setHeader("X-Concurrency-Rejected-Dimension",
                        rejectedDimension.name().toLowerCase(Locale.ROOT));
            }
            response.setHeader("Retry-After", Integer.toString(responseProperties.getRetryAfterSeconds()));
        }
    }

    private static String headerSuffix(CocoConcurrencyLimitDimension dimension) {
        String name = dimension.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
