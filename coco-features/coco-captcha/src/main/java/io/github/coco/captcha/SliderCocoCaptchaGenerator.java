package io.github.coco.captcha;

import java.security.SecureRandom;

/**
 * 滑块验证码参考生成器。
 * <p>
 * 挑战为滑轨宽度(像素),答案为目标偏移(像素)。客户端把滑块拖到目标处并提交偏移,核验时按容差比对
 * (|提交 - 目标| ≤ tolerance)。这是最小可用的服务端逻辑——不含拼图缺口图像;需要真实图形拼图的业务
 * 可注册自定义生成器,只要挑战/答案仍是本类型约定的数字即可复用同一存储与核验流程。
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
public final class SliderCocoCaptchaGenerator implements CocoCaptchaGenerator {

    private static final int TRACK_WIDTH = 280;

    private static final int MIN_OFFSET = 40;

    private final SecureRandom random = new SecureRandom();

    private final int tolerance;

    /**
     * 用默认容差(5 像素)创建。
     */
    public SliderCocoCaptchaGenerator() {
        this(5);
    }

    /**
     * 用指定容差创建。
     * @param tolerance 允许的偏移误差(像素),不能为负
     */
    public SliderCocoCaptchaGenerator(int tolerance) {
        if (tolerance < 0) {
            throw new IllegalArgumentException("tolerance must not be negative");
        }
        this.tolerance = tolerance;
    }

    @Override
    public CocoCaptchaType supportedType() {
        return CocoCaptchaType.SLIDER;
    }

    @Override
    public CocoCaptcha generate(String captchaId) {
        // Target somewhere in [MIN_OFFSET, TRACK_WIDTH - MIN_OFFSET].
        int target = MIN_OFFSET + this.random.nextInt(TRACK_WIDTH - 2 * MIN_OFFSET);
        return new CocoCaptcha(captchaId, CocoCaptchaType.SLIDER,
                Integer.toString(TRACK_WIDTH), Integer.toString(target));
    }

    @Override
    public boolean matches(String submitted, String storedAnswer) {
        if (submitted == null) {
            return false;
        }
        try {
            int offset = Integer.parseInt(submitted.strip());
            int target = Integer.parseInt(storedAnswer);
            return Math.abs(offset - target) <= this.tolerance;
        }
        catch (NumberFormatException exception) {
            return false;
        }
    }
}
