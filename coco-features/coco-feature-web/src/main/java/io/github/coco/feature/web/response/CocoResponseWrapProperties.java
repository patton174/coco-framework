package io.github.coco.feature.web.response;

/**
 * Coco Web 正常响应包装配置。
 * <p>
 * 维护正常响应包装的开关、成功响应编码和成功消息编码。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoResponseWrapProperties {

    private static final String DEFAULT_SUCCESS_CODE = "coco.success";

    private static final String DEFAULT_SUCCESS_MESSAGE_CODE = "coco.web.response.success";

    /**
     * 是否启用正常响应包装。
     */
    private boolean enabled = true;

    /**
     * 成功响应编码。
     */
    private String successCode = DEFAULT_SUCCESS_CODE;

    /**
     * 成功消息国际化编码。
     */
    private String successMessageCode = DEFAULT_SUCCESS_MESSAGE_CODE;

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
     * 返回成功响应编码。
     * </p>
     * @return 成功响应编码
     */
    public String getSuccessCode() {
        return hasText(this.successCode) ? this.successCode.trim() : DEFAULT_SUCCESS_CODE;
    }

    /**
     * <p>
     * 设置成功响应编码。
     * </p>
     * @param successCode 成功响应编码
     */
    public void setSuccessCode(String successCode) {
        this.successCode = successCode;
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
