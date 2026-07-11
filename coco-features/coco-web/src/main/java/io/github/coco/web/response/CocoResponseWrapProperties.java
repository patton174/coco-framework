package io.github.coco.web.response;

/**
 * Coco Web 正常响应包装配置。
 * <p>
 * 维护正常响应包装的开关和成功消息编码。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoResponseWrapProperties {

    private static final String DEFAULT_SUCCESS_MESSAGE_CODE = "coco.web.response.success";

    private static final long DEFAULT_MAX_BODY_BYTES = -1L;

    /**
     * 是否启用正常响应包装。
     */
    private boolean enabled = true;

    /**
     * 成功消息国际化编码。
     */
    private String successMessageCode = DEFAULT_SUCCESS_MESSAGE_CODE;

    /**
     * 正常响应包装允许的最大原始响应体字节数，负数表示不限制。
     */
    private long maxBodyBytes = DEFAULT_MAX_BODY_BYTES;

    /**
     * <p>
     * 返回是否启用正常响应包装。
     * </p>
     * @return 是否启用正常响应包装
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用正常响应包装。
     * </p>
     * @param enabled 是否启用正常响应包装
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回成功消息国际化编码。
     * </p>
     * @return 成功消息国际化编码
     */
    public String getSuccessMessageCode() {
        return hasText(this.successMessageCode) ? this.successMessageCode.trim() : DEFAULT_SUCCESS_MESSAGE_CODE;
    }

    /**
     * <p>
     * 设置成功消息国际化编码。
     * </p>
     * @param successMessageCode 成功消息国际化编码
     */
    public void setSuccessMessageCode(String successMessageCode) {
        this.successMessageCode = successMessageCode;
    }

    /**
     * <p>
     * 返回正常响应包装允许的最大原始响应体字节数。
     * </p>
     * <p>
     * 负数表示不限制。该阈值只基于已知长度判断，例如 {@code Content-Length} 或字符串响应体字节数，
     * 不会为了估算大小而提前序列化任意业务对象。
     * </p>
     * @return 最大原始响应体字节数
     */
    public long getMaxBodyBytes() {
        return this.maxBodyBytes;
    }

    /**
     * <p>
     * 设置正常响应包装允许的最大原始响应体字节数。
     * </p>
     * @param maxBodyBytes 最大原始响应体字节数；负数表示不限制
     */
    public void setMaxBodyBytes(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes < 0 ? DEFAULT_MAX_BODY_BYTES : maxBodyBytes;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
