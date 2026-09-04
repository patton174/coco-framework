package io.github.coco.captcha;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 验证码配置。
 * <p>
 * 默认关闭,需显式打开 {@code coco.captcha.enabled}。{@code ttl} 是验证码存活时间;三个 {@code *-enabled}
 * 开关控制是否装配对应类型的参考生成器(业务提供同类型生成器时以业务的为准),{@code image-length}、
 * {@code sms-code-length}、{@code slider-tolerance} 调参考实现的参数。
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
@ConfigurationProperties("coco.captcha")
public class CocoCaptchaProperties {

    private boolean enabled;

    private Duration ttl = Duration.ofMinutes(2);

    private boolean imageEnabled = true;

    private boolean sliderEnabled = true;

    private boolean smsCodeEnabled = true;

    private int imageLength = 4;

    private int smsCodeLength = 6;

    private int sliderTolerance = 5;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return this.ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public boolean isImageEnabled() {
        return this.imageEnabled;
    }

    public void setImageEnabled(boolean imageEnabled) {
        this.imageEnabled = imageEnabled;
    }

    public boolean isSliderEnabled() {
        return this.sliderEnabled;
    }

    public void setSliderEnabled(boolean sliderEnabled) {
        this.sliderEnabled = sliderEnabled;
    }

    public boolean isSmsCodeEnabled() {
        return this.smsCodeEnabled;
    }

    public void setSmsCodeEnabled(boolean smsCodeEnabled) {
        this.smsCodeEnabled = smsCodeEnabled;
    }

    public int getImageLength() {
        return this.imageLength;
    }

    public void setImageLength(int imageLength) {
        this.imageLength = imageLength;
    }

    public int getSmsCodeLength() {
        return this.smsCodeLength;
    }

    public void setSmsCodeLength(int smsCodeLength) {
        this.smsCodeLength = smsCodeLength;
    }

    public int getSliderTolerance() {
        return this.sliderTolerance;
    }

    public void setSliderTolerance(int sliderTolerance) {
        this.sliderTolerance = sliderTolerance;
    }
}
