package io.github.coco.feature.lock;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 为锁顾问确定唯一的默认锁管理器。
 */
final class CocoLockManagerResolver {

    private CocoLockManagerResolver() {
    }

    static CocoLockManager resolve(ObjectProvider<CocoLockManager> managers, ListableBeanFactory beanFactory) {
        Objects.requireNonNull(managers, "managers must not be null");
        Objects.requireNonNull(beanFactory, "beanFactory must not be null");
        try {
            CocoLockManager manager = managers.getIfUnique();
            if (manager != null) {
                return manager;
            }
        }
        catch (NoUniqueBeanDefinitionException ex) {
            // Replace Spring's diagnostic with the bounded candidate description below.
        }
        throw new IllegalStateException("Coco lock manager selection is ambiguous; candidates="
                + describeCandidates(beanFactory)
                + ". Define exactly one CocoLockManager or mark exactly one manager as @Primary");
    }

    private static String describeCandidates(ListableBeanFactory beanFactory) {
        return Arrays.stream(BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory,
                CocoLockManager.class, true, false))
                .sorted()
                .map(name -> name + "(" + typeName(beanFactory, name) + ")")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String typeName(ListableBeanFactory beanFactory, String beanName) {
        Class<?> type = beanFactory.getType(beanName, false);
        return type == null ? "unknown" : type.getName();
    }
}
