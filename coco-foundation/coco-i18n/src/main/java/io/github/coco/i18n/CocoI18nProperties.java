package io.github.coco.i18n;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.coco.i18n.internal.CocoLanguageTagNormalizer;

/**
 * Coco 国际化配置属性。
 * <p>
 * 绑定 {@code coco.common.i18n} 命名空间，控制消息资源包、默认语言和缺省消息策略。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoI18nProperties {

    private List<String> basename = mutableBasenames(List.of("coco-messages"));

    private Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;

    /**
     * 空列表表示不过滤；非空列表表示显式 opt-in 允许列表。匹配遵循 JDK {@link Locale}
     * 规范化语义，不执行 IANA 注册表别名扩展；BU/MM 等已弃用 Preferred-Value 别名保持不同。
     */
    private List<String> supportedLanguages = List.of();

    private boolean fallbackToSystemLocale;

    private boolean useCodeAsDefaultMessage = true;

    /**
     * <p>
     * 返回消息资源 basename 的可变 backing list。
     * 业务侧和 Spring Binder 可以继续通过 {@code getBasename().add(...)} 或
     * {@code getBasename().remove(...)} 更新当前配置。
     * </p>
     * @return 消息资源 basename 列表
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "The public JavaBean accessor must retain live list mutation "
                    + "compatibility for Spring Binder and existing Java consumers; "
                    + "message-source assembly takes an immutable snapshot.")
    public List<String> getBasename() {
        return this.basename;
    }

    /**
     * <p>
     * 设置消息资源 basename 列表。
     * </p>
     * @param basename 消息资源 basename 列表
     */
    public void setBasename(List<String> basename) {
        this.basename = basename == null || basename.isEmpty()
                ? mutableBasenames(List.of("coco-messages"))
                : mutableBasenames(basename);
    }

    /**
     * <p>
     * 返回默认语言。
     * </p>
     * @return 默认语言
     */
    public Locale getDefaultLocale() {
        return this.defaultLocale;
    }

    /**
     * <p>
     * 设置默认语言。
     * </p>
     * @param defaultLocale 默认语言
     */
    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale == null ? Locale.SIMPLIFIED_CHINESE : defaultLocale;
    }

    /**
     * <p>
     * 返回允许从请求或上下文直接采用的语言代码列表。
     * </p>
     * @return 受支持的 BCP 47 语言子标签列表
     */
    public List<String> getSupportedLanguages() {
        return Collections.unmodifiableList(new ArrayList<>(this.supportedLanguages));
    }

    /**
     * <p>
     * 设置允许从请求或上下文直接采用的语言代码列表。
     * </p>
     * @param supportedLanguages 受支持的 BCP 47 语言子标签列表
     */
    public void setSupportedLanguages(List<String> supportedLanguages) {
        if (supportedLanguages == null || supportedLanguages.isEmpty()) {
            this.supportedLanguages = List.of();
            return;
        }
        List<String> copiedLanguages = new ArrayList<>(supportedLanguages);
        if (copiedLanguages.stream()
                .anyMatch(language -> !CocoLanguageTagNormalizer.isValidSupportedLanguageTag(language))) {
            throw new IllegalArgumentException("supportedLanguages must contain only strict non-root BCP 47 language tags");
        }
        this.supportedLanguages = List.copyOf(copiedLanguages);
    }

    /**
     * <p>
     * 返回是否回退到系统语言。
     * </p>
     * @return 启用系统语言回退时返回 {@code true}
     */
    public boolean isFallbackToSystemLocale() {
        return this.fallbackToSystemLocale;
    }

    /**
     * <p>
     * 设置是否回退到系统语言。
     * </p>
     * @param fallbackToSystemLocale 是否回退到系统语言
     */
    public void setFallbackToSystemLocale(boolean fallbackToSystemLocale) {
        this.fallbackToSystemLocale = fallbackToSystemLocale;
    }

    /**
     * <p>
     * 返回消息资源缺失时是否使用编码作为默认消息。
     * </p>
     * @return 启用编码兜底时返回 {@code true}
     */
    public boolean isUseCodeAsDefaultMessage() {
        return this.useCodeAsDefaultMessage;
    }

    /**
     * <p>
     * 设置消息资源缺失时是否使用编码作为默认消息。
     * </p>
     * @param useCodeAsDefaultMessage 是否使用编码作为默认消息
     */
    public void setUseCodeAsDefaultMessage(boolean useCodeAsDefaultMessage) {
        this.useCodeAsDefaultMessage = useCodeAsDefaultMessage;
    }

    private static List<String> mutableBasenames(List<String> basenames) {
        return new CopyOnWriteArrayList<>(basenames);
    }

}
