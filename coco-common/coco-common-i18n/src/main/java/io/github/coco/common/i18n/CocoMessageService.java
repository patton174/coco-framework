package io.github.coco.common.i18n;

import java.util.Locale;

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

    String getMessage(String code, Object... args);

    String getMessage(String code, Locale locale, Object... args);

    String getMessageOrDefault(String code, String defaultMessage, Object... args);

    String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args);

    String resolve(CocoMessage message);

    String resolve(CocoMessage message, Locale locale);

    String resolve(CocoException exception);

    String resolve(CocoException exception, Locale locale);
}
