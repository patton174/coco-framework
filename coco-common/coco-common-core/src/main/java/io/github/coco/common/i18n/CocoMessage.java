package io.github.coco.common.i18n;

import java.util.Arrays;
import java.util.Objects;

/**
 * Coco 消息描述。
 * <p>
 * 用于在框架模块之间传递消息编码、默认文本和格式化参数，避免过早解析国际化文本。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-core}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public record CocoMessage(String code, String defaultMessage, Object... args) {

    /**
     * <p>
     * 根据消息编码契约创建 Coco 消息描述。
     * </p>
     * @param messageCode 消息编码契约
     * @param args 消息格式化参数
     */
    public CocoMessage(CocoMessageCode messageCode, Object... args) {
        this(Objects.requireNonNull(messageCode, "messageCode must not be null").code(),
                messageCode.defaultMessage(), args);
    }

    /**
     * <p>
     * 创建 Coco 消息描述，并复制格式化参数。
     * </p>
     * @param code 消息编码
     * @param defaultMessage 默认消息文本
     * @param args 消息格式化参数
     */
    public CocoMessage {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("message code must not be blank");
        }
        args = args == null ? new Object[0] : Arrays.copyOf(args, args.length);
    }

    /**
     * <p>
     * 返回消息格式化参数的防御性副本。
     * </p>
     * @return 消息格式化参数
     */
    @Override
    public Object[] args() {
        return Arrays.copyOf(this.args, this.args.length);
    }
}
