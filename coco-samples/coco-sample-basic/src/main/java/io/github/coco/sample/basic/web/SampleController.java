package io.github.coco.sample.basic.web;

import java.util.Objects;

import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextHolder;
import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.sample.basic.accesslog.SampleAccessLogRecorder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coco 基础示例接口。
 * <p>
 * 用少量接口展示统一响应、统一异常、请求上下文和接口访问日志的运行效果。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@RestController
@RequestMapping("/sample")
public class SampleController {

    private final SampleAccessLogRecorder accessLogRecorder;

    /**
     * <p>
     * 创建 Coco 基础示例接口。
     * </p>
     * @param accessLogRecorder 示例接口访问日志记录器
     */
    public SampleController(SampleAccessLogRecorder accessLogRecorder) {
        this.accessLogRecorder = Objects.requireNonNull(accessLogRecorder,
                "accessLogRecorder must not be null");
    }

    /**
     * <p>
     * 返回普通业务响应，由 Coco Web 自动包装。
     * </p>
     * @param name 示例名称
     * @return 示例问候响应
     */
    @GetMapping("/hello")
    public SampleHelloResponse hello(@RequestParam(defaultValue = "Coco") String name) {
        return new SampleHelloResponse(name, "Hello " + name);
    }

    /**
     * <p>
     * 返回当前请求上下文。
     * </p>
     * @return 示例请求上下文响应
     */
    @GetMapping("/context")
    public SampleRequestContextResponse context() {
        CocoRequestContext requestContext = CocoRequestContextHolder.current()
                .orElseThrow(() -> CocoCommonErrorCode.INTERNAL_ERROR.system("requestContext"));
        return new SampleRequestContextResponse(requestContext.traceId(),
                requestContext.method().orElse(null),
                requestContext.path().orElse(null));
    }

    /**
     * <p>
     * 抛出示例请求异常。
     * </p>
     * @return 不会返回
     */
    @GetMapping("/error")
    public SampleHelloResponse error() {
        throw CocoCommonErrorCode.INVALID_ARGUMENT.request("sampleName");
    }

    /**
     * <p>
     * 返回最近一次接口访问日志。
     * </p>
     * @return 最近一次接口访问日志响应；尚未记录时为空
     */
    @GetMapping("/access-log/latest")
    public SampleAccessLogResponse latestAccessLog() {
        return this.accessLogRecorder.latest()
                .map(SampleAccessLogResponse::from)
                .orElse(null);
    }
}
