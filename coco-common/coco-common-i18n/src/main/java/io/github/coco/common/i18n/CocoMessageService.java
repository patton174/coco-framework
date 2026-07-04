package io.github.coco.common.i18n;

import java.util.Locale;
import java.util.Objects;

import io.github.coco.common.exception.CocoException;

/**
 * Coco 消息服务。
 * <p>
 * 提供框架和业务统一使用的国际化消息解析入口。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoMessageService {

    /**
     * <p>
     * 使用当前语言解析消息编码。
     * </p>
     * @param code 消息编码
     * @param args 消息格式化参数
     * @return 解析后的消息文本
     */
    String getMessage(String code, Object... args);

    /**
     * <p>
     * 使用指定语言解析消息编码。
     * </p>
     * @param code 消息编码
     * @param locale 目标语言
     * @param args 消息格式化参数
     * @return 解析后的消息文本
     */
    String getMessage(String code, Locale locale, Object... args);

    /**
     * <p>
     * 使用当前语言解析消息编码契约。
     * </p>
     * @param messageCode 消息编码契约
     * @param args 消息格式化参数
     * @return 解析后的消息文本
     */
    default String getMessage(CocoMessageCode messageCode, Object... args) {
        return resolve(Objects.requireNonNull(messageCode, "messageCode must not be null").message(args));
    }

    /**
     * <p>
     * 使用指定语言解析消息编码契约。
     * </p>
     * @param messageCode 消息编码契约
     * @param locale 目标语言
     * @param args 消息格式化参数
     * @return 解析后的消息文本
     */
    default String getMessage(CocoMessageCode messageCode, Locale locale, Object... args) {
        return resolve(Objects.requireNonNull(messageCode, "messageCode must not be null").message(args), locale);
    }

    /**
     * <p>
     * 使用当前语言解析消息编码，并在资源缺失时返回默认文本。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param args 消息格式化参数
     * @return 解析后的消息文本
     */
    String getMessageOrDefault(String code, String defaultMessage, Object... args);

    /**
     * <p>
     * 使用指定语言解析消息编码，并在资源缺失时返回默认文本。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param locale 目标语言
     * @param args 消息格式化参数
     * @return 解析后的消息文本
     */
    String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args);

    /**
     * <p>
     * 使用当前语言解析 Coco 消息对象。
     * </p>
     * @param message Coco 消息对象
     * @return 解析后的消息文本
     */
    String resolve(CocoMessage message);

    /**
     * <p>
     * 使用指定语言解析 Coco 消息对象。
     * </p>
     * @param message Coco 消息对象
     * @param locale 目标语言
     * @return 解析后的消息文本
     */
    String resolve(CocoMessage message, Locale locale);

    /**
     * <p>
     * 使用当前语言解析 Coco 异常中的消息信息。
     * </p>
     * @param exception Coco 异常
     * @return 解析后的消息文本
     */
    String resolve(CocoException exception);

    /**
     * <p>
     * 使用指定语言解析 Coco 异常中的消息信息。
     * </p>
     * @param exception Coco 异常
     * @param locale 目标语言
     * @return 解析后的消息文本
     */
    String resolve(CocoException exception, Locale locale);
}
