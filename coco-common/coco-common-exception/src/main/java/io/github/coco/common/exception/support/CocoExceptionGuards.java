package io.github.coco.common.exception.support;

import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.common.exception.CocoErrorCode;

/**
 * Coco 异常模块内部校验工具。
 * <p>
 * 将异常模块自身的参数校验转换为带错误码的 Coco 异常，避免在构造器和工具方法中散落硬编码异常文本。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-exception}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoExceptionGuards {

    private CocoExceptionGuards() {
    }

    /**
     * <p>
     * 校验异常编码契约不为空。
     * </p>
     * @param errorCode 异常编码契约
     * @return 原异常编码契约
     */
    public static CocoErrorCode requireErrorCode(CocoErrorCode errorCode) {
        if (errorCode == null) {
            throw CocoCommonErrorCode.MISSING_ERROR_CODE.request();
        }
        return errorCode;
    }

    /**
     * <p>
     * 校验消息编码包含至少一个非空白字符。
     * </p>
     * @param code 消息编码
     * @return 原消息编码
     */
    public static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw CocoCommonErrorCode.MISSING_MESSAGE_CODE.request();
        }
        return code;
    }
}
