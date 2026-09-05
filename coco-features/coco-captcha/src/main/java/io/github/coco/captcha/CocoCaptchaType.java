package io.github.coco.captcha;

/**
 * 验证码类型。
 * <p>
 * 一个 {@link CocoCaptchaGenerator} 声明它支持的类型;生成/校验时按类型路由到对应生成器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-captcha}</li>
 * </ul>
 * @author patton174
 * @since 2.1.0
 */
public enum CocoCaptchaType {

    /** 图形验证码。挑战为 base64 PNG,答案为图中字符。 */
    IMAGE,

    /** 滑块验证码。挑战为轨道宽度,答案为目标偏移,按容差比对。 */
    SLIDER,

    /** 短信验证码。挑战为空(码经短信下发),答案为数字串。 */
    SMS_CODE
}
