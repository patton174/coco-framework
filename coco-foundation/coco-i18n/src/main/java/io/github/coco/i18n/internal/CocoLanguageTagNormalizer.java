package io.github.coco.i18n.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Coco BCP 47 语言标签校验与语义归一化工具。
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoLanguageTagNormalizer {

    private CocoLanguageTagNormalizer() {
    }

    /**
     * 校验显式支持语言标签，拒绝会被 JDK 静默丢弃的重复结构。
     * @param languageTag BCP 47 语言标签
     * @return 标签可作为显式支持语言时返回 {@code true}
     */
    public static boolean isValidSupportedLanguageTag(String languageTag) {
        if (languageTag == null || languageTag.isBlank() || "root".equalsIgnoreCase(languageTag)
                || "und".equalsIgnoreCase(languageTag)) {
            return false;
        }
        try {
            Locale locale = new Locale.Builder().setLanguageTag(languageTag).build();
            return !locale.equals(Locale.ROOT) && hasValidStructure(languageTag);
        }
        catch (IllformedLocaleException exception) {
            return false;
        }
    }

    /**
     * 生成用于完整语言标签匹配的语义归一化键。
     * @param locale 待归一化语言
     * @return 小写语义归一化键
     */
    public static String semanticKey(Locale locale) {
        String transformedExtension = locale.getExtension('t');
        if (transformedExtension == null) {
            return locale.toLanguageTag().toLowerCase(Locale.ROOT);
        }
        String normalizedExtension = normalizeTransformedExtension(transformedExtension);
        if (normalizedExtension == null) {
            return locale.toLanguageTag().toLowerCase(Locale.ROOT);
        }
        Locale normalizedLocale = new Locale.Builder()
                .setLanguageTag(locale.toLanguageTag())
                .setExtension('t', normalizedExtension)
                .build();
        return normalizedLocale.toLanguageTag().toLowerCase(Locale.ROOT);
    }

    private static boolean hasValidStructure(String languageTag) {
        String[] subtags = languageTag.toLowerCase(Locale.ROOT).split("-");
        if (!hasUniqueOuterVariants(subtags)) {
            return false;
        }
        Set<String> singletons = new HashSet<>();
        for (int index = 0; index < subtags.length; index++) {
            String subtag = subtags[index];
            if (subtag.length() != 1 || !Character.isLetterOrDigit(subtag.charAt(0))) {
                continue;
            }
            if ("x".equals(subtag)) {
                return true;
            }
            if (!singletons.add(subtag)) {
                return false;
            }
            int end = index + 1;
            while (end < subtags.length && subtags[end].length() != 1) {
                end++;
            }
            if ("u".equals(subtag) && !hasUniqueUnicodeStructure(subtags, index + 1, end)) {
                return false;
            }
            if ("t".equals(subtag)
                    && normalizeTransformedExtension(String.join("-",
                            Arrays.copyOfRange(subtags, index + 1, end))) == null) {
                return false;
            }
            index = end - 1;
        }
        return true;
    }

    private static boolean hasUniqueUnicodeStructure(String[] subtags, int start, int end) {
        Set<String> attributes = new HashSet<>();
        Set<String> keys = new HashSet<>();
        int index = start;
        while (index < end && subtags[index].length() != 2) {
            if (!attributes.add(subtags[index])) {
                return false;
            }
            index++;
        }
        for (; index < end; index++) {
            if (subtags[index].length() == 2 && !keys.add(subtags[index])) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeTransformedExtension(String extension) {
        String[] subtags = extension.toLowerCase(Locale.ROOT).split("-");
        int firstField = 0;
        while (firstField < subtags.length && !isTransformedKey(subtags[firstField])) {
            firstField++;
        }

        String transformedLanguage = "";
        if (firstField > 0) {
            transformedLanguage = normalizeTransformedLanguage(subtags, firstField);
            if (transformedLanguage == null) {
                return null;
            }
        }
        if (firstField == subtags.length) {
            return transformedLanguage.isEmpty() ? null : transformedLanguage;
        }

        List<TransformedField> fields = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int index = firstField; index < subtags.length;) {
            String key = subtags[index++];
            if (!isTransformedKey(key) || !keys.add(key)) {
                return null;
            }
            int valueStart = index;
            while (index < subtags.length && !isTransformedKey(subtags[index])) {
                if (subtags[index].length() < 3) {
                    return null;
                }
                index++;
            }
            if (valueStart == index) {
                return null;
            }
            fields.add(new TransformedField(key, List.of(subtags).subList(valueStart, index)));
        }
        fields.sort(Comparator.comparing(TransformedField::key));

        StringJoiner normalized = new StringJoiner("-");
        if (!transformedLanguage.isEmpty()) {
            normalized.add(transformedLanguage);
        }
        for (TransformedField field : fields) {
            normalized.add(field.key());
            field.values().forEach(normalized::add);
        }
        return normalized.toString();
    }

    private static String normalizeTransformedLanguage(String[] subtags, int end) {
        String languageTag = String.join("-", Arrays.copyOfRange(subtags, 0, end));
        try {
            Locale locale = new Locale.Builder().setLanguageTag(languageTag).build();
            if (locale.getLanguage().isEmpty() || !hasUniqueOuterVariants(Arrays.copyOf(subtags, end))) {
                return null;
            }
            return locale.toLanguageTag().toLowerCase(Locale.ROOT);
        }
        catch (IllformedLocaleException exception) {
            return null;
        }
    }

    private static boolean hasUniqueOuterVariants(String[] subtags) {
        if (subtags.length == 0 || subtags[0].length() == 1) {
            return true;
        }
        int index = 1;
        if (subtags[0].length() <= 3) {
            int extlangCount = 0;
            while (index < subtags.length && extlangCount < 3
                    && subtags[index].length() == 3 && isAlpha(subtags[index])) {
                index++;
                extlangCount++;
            }
        }
        if (index < subtags.length && subtags[index].length() == 4 && isAlpha(subtags[index])) {
            index++;
        }
        if (index < subtags.length && isRegion(subtags[index])) {
            index++;
        }
        Set<String> variants = new HashSet<>();
        while (index < subtags.length && isVariant(subtags[index])) {
            if (!variants.add(subtags[index])) {
                return false;
            }
            index++;
        }
        return true;
    }

    private static boolean isRegion(String subtag) {
        return (subtag.length() == 2 && isAlpha(subtag))
                || (subtag.length() == 3 && subtag.chars().allMatch(Character::isDigit));
    }

    private static boolean isVariant(String subtag) {
        return subtag.length() >= 5 && subtag.length() <= 8
                || subtag.length() == 4 && Character.isDigit(subtag.charAt(0));
    }

    private static boolean isTransformedKey(String subtag) {
        return subtag.length() == 2 && Character.isLetter(subtag.charAt(0))
                && Character.isDigit(subtag.charAt(1));
    }

    private static boolean isAlpha(String subtag) {
        return subtag.chars().allMatch(Character::isLetter);
    }

    private record TransformedField(String key, List<String> values) {
    }
}
