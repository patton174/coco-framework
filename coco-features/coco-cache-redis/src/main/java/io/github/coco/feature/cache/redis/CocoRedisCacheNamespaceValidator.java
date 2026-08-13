package io.github.coco.feature.cache.redis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Redis 缓存命名空间校验器。 */
final class CocoRedisCacheNamespaceValidator {

    private static final int MAX_NAMESPACE_LENGTH = 128;

    private CocoRedisCacheNamespaceValidator() {
    }

    static void validate(String value, String propertyName) {
        if (!isSafe(value)) {
            throw new IllegalStateException(propertyName + " must be nonblank, at most " + MAX_NAMESPACE_LENGTH
                    + " characters, and contain no whitespace, control characters, braces, or Redis glob metacharacters");
        }
    }

    static Set<String> validateCacheNames(List<String> configuredNames) {
        Set<String> names = new LinkedHashSet<>();
        for (String cacheName : configuredNames) {
            validate(cacheName, "coco.cache.redis.cache-names");
            if (!names.add(cacheName)) {
                throw new IllegalStateException("coco.cache.redis.cache-names must be unique");
            }
        }
        return names;
    }

    private static boolean isSafe(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_NAMESPACE_LENGTH
                && value.chars().noneMatch(CocoRedisCacheNamespaceValidator::isUnsafeCharacter);
    }

    private static boolean isUnsafeCharacter(int character) {
        return Character.isISOControl(character) || Character.isWhitespace(character)
                || character == '{' || character == '}'
                || character == '*' || character == '?' || character == '[' || character == ']'
                || character == '\\';
    }
}
