package io.github.coco.feature.web.context;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 请求快照请求属性工具。
 * <p>
 * 在同一次 Servlet 请求内保存已经解析完成的 {@link CocoWebRequestSnapshot}，供 Trace、Sign、AES 和防重放等框架过滤器复用。
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
public final class CocoWebRequestSnapshotAttributes {

    /**
     * 已解析 Web 请求快照请求属性名。
     */
    public static final String ATTRIBUTE_NAME = CocoWebRequestSnapshotAttributes.class.getName()
            + ".requestSnapshot";

    /**
     * 已解析 Web 请求快照对应的请求头指纹属性名。
     */
    public static final String HEADER_FINGERPRINT_ATTRIBUTE_NAME = CocoWebRequestSnapshotAttributes.class.getName()
            + ".headerFingerprint";

    private CocoWebRequestSnapshotAttributes() {
    }

    /**
     * <p>
     * 从当前请求读取已解析 Web 请求快照。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 已解析 Web 请求快照；尚未解析时为空
     */
    public static Optional<CocoWebRequestSnapshot> get(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        Object attribute = request.getAttribute(ATTRIBUTE_NAME);
        return attribute instanceof CocoWebRequestSnapshot snapshot ? Optional.of(snapshot) : Optional.empty();
    }

    /**
     * <p>
     * 保存已解析 Web 请求快照到当前请求。
     * </p>
     * @param request 当前 HTTP 请求
     * @param snapshot 已解析 Web 请求快照
     */
    public static void set(HttpServletRequest request, CocoWebRequestSnapshot snapshot) {
        if (request != null && snapshot != null) {
            request.setAttribute(ATTRIBUTE_NAME, snapshot);
        }
    }

    /**
     * <p>
     * 保存已解析 Web 请求快照及其请求头指纹到当前请求。
     * </p>
     * @param request 当前 HTTP 请求
     * @param snapshot 已解析 Web 请求快照
     * @param headerFingerprint 请求头指纹
     */
    public static void set(HttpServletRequest request, CocoWebRequestSnapshot snapshot, String headerFingerprint) {
        set(request, snapshot);
        if (request != null && headerFingerprint != null) {
            request.setAttribute(HEADER_FINGERPRINT_ATTRIBUTE_NAME, headerFingerprint);
        }
    }

    /**
     * <p>
     * 从当前请求读取已解析 Web 请求快照对应的请求头指纹。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 请求头指纹；尚未解析时为空
     */
    public static Optional<String> headerFingerprint(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        Object attribute = request.getAttribute(HEADER_FINGERPRINT_ATTRIBUTE_NAME);
        return attribute instanceof String fingerprint ? Optional.of(fingerprint) : Optional.empty();
    }

    /**
     * <p>
     * 清理当前请求上的 Web 请求快照。
     * </p>
     * @param request 当前 HTTP 请求
     */
    public static void clear(HttpServletRequest request) {
        if (request != null) {
            request.removeAttribute(ATTRIBUTE_NAME);
            request.removeAttribute(HEADER_FINGERPRINT_ATTRIBUTE_NAME);
        }
    }
}
