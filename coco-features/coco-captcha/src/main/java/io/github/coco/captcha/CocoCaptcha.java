package io.github.coco.captcha;

import java.util.Objects;

/**
 * 一次生成的验证码。
 * <p>
 * 拆成两部分:{@code challenge} 是可以安全下发给客户端的挑战(图形 base64、滑块轨道宽度等),
 * {@code answer} 是只存服务端、用于后续校验的答案。{@link #toClientView()} 产出去掉答案的视图,
 * 避免答案随响应泄露。
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
public record CocoCaptcha(String captchaId, CocoCaptchaType type, String challenge, String answer) {

    /**
     * 校验四个字段非空。
     * @param captchaId 验证码唯一标识
     * @param type 验证码类型
     * @param challenge 客户端可见的挑战内容
     * @param answer 服务端保留的答案
     */
    public CocoCaptcha {
        Objects.requireNonNull(captchaId, "captchaId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(challenge, "challenge must not be null");
        Objects.requireNonNull(answer, "answer must not be null");
    }

    /**
     * 产出不含答案的客户端视图。
     * @return 仅含标识、类型、挑战的视图
     */
    public ClientView toClientView() {
        return new ClientView(this.captchaId, this.type, this.challenge);
    }

    /**
     * 验证码的客户端视图,不含答案。
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
    public record ClientView(String captchaId, CocoCaptchaType type, String challenge) {
    }
}
