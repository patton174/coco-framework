package io.github.coco.feature.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明需要发布业务审计事件的 Spring Bean 类型或方法。
 * <p>
 * 方法声明优先于类型声明。所有字段只作为静态审计元数据处理，不支持表达式，也不会读取方法参数、返回值或异常内容。
 * </p>
 * <p>
 * {@code type} 在实际调用前去除首尾空白后必须非空；其余字段为空时不写入默认审计事件。
 * </p>
 * @author patton174
 * @since 2.0.3
 */
@Documented
@Inherited
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface CocoAudited {

    /**
     * 返回审计事件类型。
     * @return 审计事件类型
     */
    String type();

    /**
     * 返回静态审计动作。
     * @return 审计动作
     */
    String action() default "";

    /**
     * 返回静态资源类型。
     * @return 资源类型
     */
    String resourceType() default "";

    /**
     * 返回静态资源标识。
     * @return 资源标识
     */
    String resourceId() default "";
}
