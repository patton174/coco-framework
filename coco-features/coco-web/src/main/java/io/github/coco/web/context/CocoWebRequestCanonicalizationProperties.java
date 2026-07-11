package io.github.coco.web.context;

/**
 * Coco Web 请求规范化配置属性。
 * <p>
 * 控制请求规范化文本包含哪些稳定字段，供 Sign 验签、请求摘要、防重放扩展和后续安全能力复用。
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
public class CocoWebRequestCanonicalizationProperties {

    private static final String DEFAULT_PARAMETER_VALUE_SEPARATOR = ",";

    private static final String DEFAULT_VERSION = "coco-v2";

    private String version = DEFAULT_VERSION;

    private boolean includeVersion = false;

    private boolean includePurpose = false;

    private boolean includeMethod = true;

    private boolean includePath = true;

    private boolean includeQueryString = true;

    private boolean includeHeaders = true;

    private boolean includeCookies = false;

    private boolean includeBrowserFingerprint = false;

    private boolean includeBrowserFingerprintSignals = false;

    private boolean includeParameters = true;

    private boolean includeParameterSources = true;

    private boolean includeBodySha256 = true;

    private boolean includeBodyLength = true;

    private boolean sortParameterValues = true;

    private String parameterValueSeparator = DEFAULT_PARAMETER_VALUE_SEPARATOR;

    /**
     * <p>
     * 返回请求规范化协议版本。
     * </p>
     * @return 请求规范化协议版本
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * <p>
     * 设置请求规范化协议版本。
     * </p>
     * @param version 请求规范化协议版本
     */
    public void setVersion(String version) {
        this.version = version == null || version.isBlank() ? DEFAULT_VERSION : version.trim();
    }

    /**
     * <p>
     * 返回是否将规范化协议版本写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeVersion() {
        return this.includeVersion;
    }

    /**
     * <p>
     * 设置是否将规范化协议版本写入规范化文本。
     * </p>
     * @param includeVersion 是否写入规范化协议版本
     */
    public void setIncludeVersion(boolean includeVersion) {
        this.includeVersion = includeVersion;
    }

    /**
     * <p>
     * 返回是否将规范化用途写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludePurpose() {
        return this.includePurpose;
    }

    /**
     * <p>
     * 设置是否将规范化用途写入规范化文本。
     * </p>
     * @param includePurpose 是否写入规范化用途
     */
    public void setIncludePurpose(boolean includePurpose) {
        this.includePurpose = includePurpose;
    }

    /**
     * <p>
     * 返回是否将 HTTP 方法写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeMethod() {
        return this.includeMethod;
    }

    /**
     * <p>
     * 设置是否将 HTTP 方法写入规范化文本。
     * </p>
     * @param includeMethod 是否写入 HTTP 方法
     */
    public void setIncludeMethod(boolean includeMethod) {
        this.includeMethod = includeMethod;
    }

    /**
     * <p>
     * 返回是否将请求路径写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludePath() {
        return this.includePath;
    }

    /**
     * <p>
     * 设置是否将请求路径写入规范化文本。
     * </p>
     * @param includePath 是否写入请求路径
     */
    public void setIncludePath(boolean includePath) {
        this.includePath = includePath;
    }

    /**
     * <p>
     * 返回是否将原始查询字符串写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeQueryString() {
        return this.includeQueryString;
    }

    /**
     * <p>
     * 设置是否将原始查询字符串写入规范化文本。
     * </p>
     * @param includeQueryString 是否写入原始查询字符串
     */
    public void setIncludeQueryString(boolean includeQueryString) {
        this.includeQueryString = includeQueryString;
    }

    /**
     * <p>
     * 返回是否将规范化请求头写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeHeaders() {
        return this.includeHeaders;
    }

    /**
     * <p>
     * 设置是否将规范化请求头写入规范化文本。
     * </p>
     * @param includeHeaders 是否写入规范化请求头
     */
    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    /**
     * <p>
     * 返回是否将规范化 Cookie 写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeCookies() {
        return this.includeCookies;
    }

    /**
     * <p>
     * 设置是否将规范化 Cookie 写入规范化文本。
     * </p>
     * @param includeCookies 是否写入规范化 Cookie
     */
    public void setIncludeCookies(boolean includeCookies) {
        this.includeCookies = includeCookies;
    }

    /**
     * <p>
     * 返回是否将浏览器指纹摘要写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeBrowserFingerprint() {
        return this.includeBrowserFingerprint;
    }

    /**
     * <p>
     * 设置是否将浏览器指纹摘要写入规范化文本。
     * </p>
     * @param includeBrowserFingerprint 是否写入浏览器指纹摘要
     */
    public void setIncludeBrowserFingerprint(boolean includeBrowserFingerprint) {
        this.includeBrowserFingerprint = includeBrowserFingerprint;
    }

    /**
     * <p>
     * 返回是否将浏览器指纹信号写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeBrowserFingerprintSignals() {
        return this.includeBrowserFingerprintSignals;
    }

    /**
     * <p>
     * 设置是否将浏览器指纹信号写入规范化文本。
     * </p>
     * @param includeBrowserFingerprintSignals 是否写入浏览器指纹信号
     */
    public void setIncludeBrowserFingerprintSignals(boolean includeBrowserFingerprintSignals) {
        this.includeBrowserFingerprintSignals = includeBrowserFingerprintSignals;
    }

    /**
     * <p>
     * 返回是否将请求参数写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeParameters() {
        return this.includeParameters;
    }

    /**
     * <p>
     * 设置是否将请求参数写入规范化文本。
     * </p>
     * @param includeParameters 是否写入请求参数
     */
    public void setIncludeParameters(boolean includeParameters) {
        this.includeParameters = includeParameters;
    }

    /**
     * <p>
     * 返回是否在支持分源的规范化版本中输出查询参数和请求体参数分段。
     * </p>
     * @return 输出分源参数时返回 {@code true}
     */
    public boolean isIncludeParameterSources() {
        return this.includeParameterSources;
    }

    /**
     * <p>
     * 设置是否在支持分源的规范化版本中输出查询参数和请求体参数分段。
     * </p>
     * @param includeParameterSources 是否输出分源参数
     */
    public void setIncludeParameterSources(boolean includeParameterSources) {
        this.includeParameterSources = includeParameterSources;
    }

    /**
     * <p>
     * 返回是否将请求体 SHA-256 摘要写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeBodySha256() {
        return this.includeBodySha256;
    }

    /**
     * <p>
     * 设置是否将请求体 SHA-256 摘要写入规范化文本。
     * </p>
     * @param includeBodySha256 是否写入请求体 SHA-256 摘要
     */
    public void setIncludeBodySha256(boolean includeBodySha256) {
        this.includeBodySha256 = includeBodySha256;
    }

    /**
     * <p>
     * 返回是否将请求体长度写入规范化文本。
     * </p>
     * @return 写入时返回 {@code true}
     */
    public boolean isIncludeBodyLength() {
        return this.includeBodyLength;
    }

    /**
     * <p>
     * 设置是否将请求体长度写入规范化文本。
     * </p>
     * @param includeBodyLength 是否写入请求体长度
     */
    public void setIncludeBodyLength(boolean includeBodyLength) {
        this.includeBodyLength = includeBodyLength;
    }

    /**
     * <p>
     * 返回是否对同名请求参数值排序。
     * </p>
     * @return 排序时返回 {@code true}
     */
    public boolean isSortParameterValues() {
        return this.sortParameterValues;
    }

    /**
     * <p>
     * 设置是否对同名请求参数值排序。
     * </p>
     * @param sortParameterValues 是否排序参数值
     */
    public void setSortParameterValues(boolean sortParameterValues) {
        this.sortParameterValues = sortParameterValues;
    }

    /**
     * <p>
     * 返回同名请求参数多值分隔符。
     * </p>
     * @return 同名请求参数多值分隔符
     */
    public String getParameterValueSeparator() {
        return this.parameterValueSeparator;
    }

    /**
     * <p>
     * 设置同名请求参数多值分隔符。
     * </p>
     * @param parameterValueSeparator 同名请求参数多值分隔符
     */
    public void setParameterValueSeparator(String parameterValueSeparator) {
        this.parameterValueSeparator = parameterValueSeparator == null || parameterValueSeparator.isBlank()
                ? DEFAULT_PARAMETER_VALUE_SEPARATOR
                : parameterValueSeparator;
    }
}
