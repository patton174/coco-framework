package io.github.coco.compatibility.security;

import io.github.coco.feature.security.CocoSecurity;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.CocoSecurityErrorCode;
import io.github.coco.feature.security.CocoSecurityFeature;
import io.github.coco.feature.security.CocoSecurityProperties;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.context.HolderCocoSecurityContextResolver;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoSecurityWebHeaderProperties;
import io.github.coco.feature.security.web.CocoSecurityWebProperties;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import io.github.coco.feature.security.web.HeaderCocoWebSecurityContextResolver;

/**
 * {@code coco-feature-security} 兼容门面编译探针。
 * <p>
 * 以编译期引用的方式钉住旧坐标 {@code coco-feature-security} 曾经对外暴露、现由 {@code coco-security} 承载的公开类型。
 * 只要业务项目仍通过旧坐标引入依赖，这些类型就必须继续可解析；任何一个类型被移动、改名或降低可见性，
 * 本模块的测试编译都会失败，从而在发布前暴露兼容面的破坏。该类不含测试方法，运行期也不会被加载。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-security}</li>
 * </ul>
 * @author patton174
 * @since 3.0.0
 */
final class CocoFeatureSecurityCompatibilityCompileProbe {

    /** 旧坐标消费者可能直接引用的公开类型；仅用于编译期校验，不会被读取。 */
    private static final Class<?>[] PUBLIC_TYPES = {
            CocoSecurity.class,
            CocoSecurityAutoConfiguration.class,
            CocoSecurityErrorCode.class,
            CocoSecurityFeature.class,
            CocoSecurityProperties.class,
            CocoSecurityContext.class,
            CocoSecurityContextHolder.class,
            CocoSecurityContextResolver.class,
            CocoSecurityPrincipal.class,
            HolderCocoSecurityContextResolver.class,
            CocoSecurityWebFilter.class,
            CocoSecurityWebHeaderProperties.class,
            CocoSecurityWebProperties.class,
            CocoWebSecurityContextResolver.class,
            HeaderCocoWebSecurityContextResolver.class
    };

    private CocoFeatureSecurityCompatibilityCompileProbe() {
    }
}
