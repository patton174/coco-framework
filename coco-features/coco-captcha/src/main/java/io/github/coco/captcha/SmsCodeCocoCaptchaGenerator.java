package io.github.coco.captcha;

import java.security.SecureRandom;

/**
 * 短信验证码参考生成器。
 * <p>
 * 生成一段数字码作为答案,挑战内容为空——码本身应由业务侧通过 {@code coco-notification} 的短信渠道下发,
 * 本模块只负责生成与核验,不发送。校验去空白后精确比对。仅为参考实现,业务可注册自定义生成器调整码长、
 * 字符集等。
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
public final class SmsCodeCocoCaptchaGenerator implements CocoCaptchaGenerator {

    private final SecureRandom random = new SecureRandom();

    private final int length;

    /**
     * 用默认码长(6)创建。
     */
    public SmsCodeCocoCaptchaGenerator() {
        this(6);
    }

    /**
     * 用指定码长创建。
     * @param length 数字码位数,必须为正
     */
    public SmsCodeCocoCaptchaGenerator(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        this.length = length;
    }

    @Override
    public CocoCaptchaType supportedType() {
        return CocoCaptchaType.SMS_CODE;
    }

    @Override
    public CocoCaptcha generate(String captchaId) {
        StringBuilder code = new StringBuilder(this.length);
        for (int i = 0; i < this.length; i++) {
            code.append(this.random.nextInt(10));
        }
        // Challenge is empty: the code travels out-of-band via SMS, not in the response.
        return new CocoCaptcha(captchaId, CocoCaptchaType.SMS_CODE, "", code.toString());
    }

    @Override
    public boolean matches(String submitted, String storedAnswer) {
        return submitted != null && submitted.strip().equals(storedAnswer);
    }
}
