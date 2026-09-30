package io.github.coco.compatibility.audit;

import io.github.coco.feature.audit.CocoAuditAutoConfiguration;
import io.github.coco.feature.audit.CocoAuditFeature;
import io.github.coco.feature.audit.CocoAuditProperties;
import io.github.coco.feature.audit.accesslog.CocoAccessLogAuditRecorder;
import io.github.coco.feature.audit.core.CocoAuditErrorHandler;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditFailurePolicy;
import io.github.coco.feature.audit.core.CocoAuditFormatter;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.audit.core.CompositeCocoAuditPublisher;
import io.github.coco.feature.audit.core.DefaultCocoAuditFormatter;
import io.github.coco.feature.audit.core.LoggingCocoAuditRecorder;
import io.github.coco.feature.audit.core.NoOpCocoAuditRecorder;
import io.github.coco.feature.audit.core.PolicyCocoAuditErrorHandler;

/**
 * {@code coco-feature-audit} 兼容门面编译探针。
 * <p>
 * 以编译期引用的方式钉住旧坐标 {@code coco-feature-audit} 曾经对外暴露、现由 {@code coco-audit} 承载的公开类型。
 * 只要业务项目仍通过旧坐标引入依赖，这些类型就必须继续可解析；任何一个类型被移动、改名或降低可见性，
 * 本模块的测试编译都会失败，从而在发布前暴露兼容面的破坏。该类不含测试方法，运行期也不会被加载。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-audit}</li>
 * </ul>
 * @author patton174
 * @since 3.0.0
 */
final class CocoFeatureAuditCompatibilityCompileProbe {

    /** 旧坐标消费者可能直接引用的公开类型；仅用于编译期校验，不会被读取。 */
    private static final Class<?>[] PUBLIC_TYPES = {
            CocoAuditFeature.class,
            CocoAuditAutoConfiguration.class,
            CocoAuditProperties.class,
            CocoAccessLogAuditRecorder.class,
            CocoAuditErrorHandler.class,
            CocoAuditEvent.class,
            CocoAuditFailurePolicy.class,
            CocoAuditFormatter.class,
            CocoAuditPublisher.class,
            CocoAuditRecorder.class,
            CompositeCocoAuditPublisher.class,
            DefaultCocoAuditFormatter.class,
            LoggingCocoAuditRecorder.class,
            NoOpCocoAuditRecorder.class,
            PolicyCocoAuditErrorHandler.class
    };

    private CocoFeatureAuditCompatibilityCompileProbe() {
    }
}
