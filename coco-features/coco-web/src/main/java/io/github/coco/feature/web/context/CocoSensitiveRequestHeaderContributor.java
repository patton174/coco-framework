package io.github.coco.feature.web.context;

import java.util.Set;

/** 向统一 Web 请求上下文贡献需要采集并脱敏的敏感请求头。 */
@FunctionalInterface
public interface CocoSensitiveRequestHeaderContributor {
    /** @return 需要在访问日志上下文中掩码的请求头名称 */
    Set<String> headerNames();
}
