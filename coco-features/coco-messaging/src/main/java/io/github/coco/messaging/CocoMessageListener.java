package io.github.coco.messaging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个 Coco 消息监听方法。
 * <p>
 * 被注解的方法必须是 public、非 static、返回 void，并且仅接受一个参数：{@link CocoMessageEnvelope} 或业务负载类型。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CocoMessageListener {

    /** @return 订阅主题 */
    String topic();

    /**
     * 返回同主题监听器的顺序。数值较小的监听器先执行；相同数值按 Bean 名称和方法签名排序。
     * @return 监听顺序
     */
    int order() default 0;
}
