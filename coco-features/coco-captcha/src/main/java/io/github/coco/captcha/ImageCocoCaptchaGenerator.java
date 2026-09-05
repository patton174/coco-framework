package io.github.coco.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.imageio.ImageIO;

/**
 * 图形验证码参考生成器。
 * <p>
 * 随机生成一段字符,渲染成 PNG 并以 {@code data:image/png;base64,...} 形式作为挑战下发;答案是那段
 * 字符,校验时去除空白并大小写不敏感精确比对。用 JDK 内置 AWT/ImageIO,不引入第三方图像库。
 * 仅为可用的参考实现,生产可注册自定义生成器替换字体、干扰线、扭曲等。
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
public final class ImageCocoCaptchaGenerator implements CocoCaptchaGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int WIDTH = 130;

    private static final int HEIGHT = 48;

    private final SecureRandom random = new SecureRandom();

    private final int length;

    /**
     * 用默认长度(4)创建。
     */
    public ImageCocoCaptchaGenerator() {
        this(4);
    }

    /**
     * 用指定字符长度创建。
     * @param length 验证码字符数,必须为正
     */
    public ImageCocoCaptchaGenerator(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        this.length = length;
    }

    @Override
    public CocoCaptchaType supportedType() {
        return CocoCaptchaType.IMAGE;
    }

    @Override
    public CocoCaptcha generate(String captchaId) {
        StringBuilder text = new StringBuilder(this.length);
        for (int i = 0; i < this.length; i++) {
            text.append(ALPHABET.charAt(this.random.nextInt(ALPHABET.length())));
        }
        String dataUri = "data:image/png;base64," + render(text.toString());
        return new CocoCaptcha(captchaId, CocoCaptchaType.IMAGE, dataUri, text.toString());
    }

    @Override
    public boolean matches(String submitted, String storedAnswer) {
        return submitted != null && submitted.strip().equalsIgnoreCase(storedAnswer);
    }

    private String render(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            for (int i = 0; i < text.length(); i++) {
                graphics.setColor(new Color(this.random.nextInt(128), this.random.nextInt(128),
                        this.random.nextInt(128)));
                int x = 12 + i * ((WIDTH - 20) / text.length());
                int y = 26 + this.random.nextInt(12);
                graphics.drawString(String.valueOf(text.charAt(i)), x, y);
            }
            // A couple of interference lines to make trivial OCR a little harder.
            for (int i = 0; i < 4; i++) {
                graphics.setColor(new Color(this.random.nextInt(200), this.random.nextInt(200),
                        this.random.nextInt(200)));
                graphics.drawLine(this.random.nextInt(WIDTH), this.random.nextInt(HEIGHT),
                        this.random.nextInt(WIDTH), this.random.nextInt(HEIGHT));
            }
        }
        finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("failed to encode captcha image", exception);
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
