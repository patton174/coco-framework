package io.github.coco.feature.security.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Spring Bean 方法或类型的静态授权要求。
 * <p>
 * 未声明角色和权限时仅要求已认证。角色组和权限组分别依照其组合方式判定，两个非空组固定同时满足。
 * 方法上的声明完整覆盖类型上的声明；该注解不支持表达式、通配符、角色层级或动态参数解析。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
@Inherited
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface CocoAuthorize {

    /**
     * 要求的角色编码。
     * @return 角色编码
     */
    String[] roles() default {};

    /**
     * 角色组的组合方式。
     * @return 角色组组合方式
     */
    CocoAuthorizationMode roleMode() default CocoAuthorizationMode.ALL;

    /**
     * 要求的权限编码。
     * @return 权限编码
     */
    String[] permissions() default {};

    /**
     * 权限组的组合方式。
     * @return 权限组组合方式
     */
    CocoAuthorizationMode permissionMode() default CocoAuthorizationMode.ALL;
}
