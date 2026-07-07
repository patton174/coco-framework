package io.github.coco.feature.web.context;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * <p>
 * Coco IP 地址支持工具。
 * </p>
 * <p>
 * 统一封装代理信任判断和 IP 字面量解析逻辑，供客户端 IP 解析、反向代理目标解析等基础设施复用。
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
public final class CocoIpAddressSupport {

    private CocoIpAddressSupport() {
    }

    /**
     * <p>
     * 判断远端地址是否命中可信代理网段。
     * </p>
     * @param remoteAddress 远端地址
     * @param trustedProxyCidrs 可信代理网段集合
     * @return 命中可信代理网段时返回 {@code true}
     */
    public static boolean isTrustedProxy(String remoteAddress, Set<String> trustedProxyCidrs) {
        if (remoteAddress == null || remoteAddress.isBlank()
                || trustedProxyCidrs == null || trustedProxyCidrs.isEmpty()) {
            return false;
        }
        byte[] remoteAddressBytes = parseIpAddress(remoteAddress);
        if (remoteAddressBytes == null) {
            return false;
        }
        return trustedProxyCidrs.stream()
                .anyMatch(trustedProxy -> matchesTrustedProxy(remoteAddressBytes, trustedProxy));
    }

    /**
     * <p>
     * 解析 IP 地址字面量。
     * </p>
     * @param value IP 地址字符串
     * @return IP 地址字节数组；无法解析时返回 {@code null}
     */
    public static byte[] parseIpAddress(String value) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return null;
        }
        if (isIpv4Literal(normalized)) {
            return parseIpv4Address(normalized);
        }
        if (!normalized.contains(":") || !isIpv6LiteralCandidate(normalized)) {
            return null;
        }
        try {
            byte[] address = InetAddress.getByName(normalized).getAddress();
            return address.length == 16 ? address : null;
        }
        catch (UnknownHostException ex) {
            return null;
        }
    }

    /**
     * <p>
     * 归一化可选字符串。
     * </p>
     * @param value 原始字符串
     * @return 去除首尾空白后的字符串；为空时返回 {@code null}
     */
    public static String normalizeString(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean matchesTrustedProxy(byte[] remoteAddress, String trustedProxy) {
        String normalizedProxy = normalizeString(trustedProxy);
        if (normalizedProxy == null) {
            return false;
        }
        int separatorIndex = normalizedProxy.indexOf('/');
        String addressPart = separatorIndex < 0 ? normalizedProxy : normalizedProxy.substring(0, separatorIndex);
        byte[] trustedAddress = parseIpAddress(addressPart);
        if (trustedAddress == null || trustedAddress.length != remoteAddress.length) {
            return false;
        }
        int prefixLength = separatorIndex < 0
                ? remoteAddress.length * Byte.SIZE
                : parsePrefixLength(normalizedProxy.substring(separatorIndex + 1), remoteAddress.length * Byte.SIZE);
        return prefixLength >= 0 && matchesPrefix(remoteAddress, trustedAddress, prefixLength);
    }

    private static boolean matchesPrefix(byte[] remoteAddress, byte[] trustedAddress, int prefixLength) {
        int fullBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        for (int index = 0; index < fullBytes; index++) {
            if (remoteAddress[index] != trustedAddress[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (Byte.SIZE - remainingBits);
        return (remoteAddress[fullBytes] & mask) == (trustedAddress[fullBytes] & mask);
    }

    private static int parsePrefixLength(String value, int maxPrefixLength) {
        try {
            int prefixLength = Integer.parseInt(value);
            return prefixLength >= 0 && prefixLength <= maxPrefixLength ? prefixLength : -1;
        }
        catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static boolean isIpv4Literal(String value) {
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static byte[] parseIpv4Address(String value) {
        String[] parts = value.split("\\.");
        byte[] address = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            int part = Integer.parseInt(parts[index]);
            if (part < 0 || part > 255) {
                return null;
            }
            address[index] = (byte) part;
        }
        return address;
    }

    private static boolean isIpv6LiteralCandidate(String value) {
        return value.chars().allMatch(character ->
                Character.digit(character, 16) >= 0 || character == ':' || character == '.');
    }
}
