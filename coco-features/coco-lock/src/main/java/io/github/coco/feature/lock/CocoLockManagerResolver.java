package io.github.coco.feature.lock;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 基于非 eager 可见候选的元数据为锁顾问确定默认锁管理器。
 *
 * <p>未知产品类型的 {@code FactoryBean} 在非 eager 枚举中不可见，本解析器不会实例化它们进行探测。
 * 因此，解析契约仅覆盖该非 eager 可见集合，这类 {@code FactoryBean} 后续产生的额外管理器不在静态消歧范围内。
 */
final class CocoLockManagerResolver {

    private CocoLockManagerResolver() {
    }

    static CocoLockManager resolve(ConfigurableListableBeanFactory beanFactory) {
        Objects.requireNonNull(beanFactory, "beanFactory must not be null");
        List<Candidate> candidates = candidates(beanFactory);
        if (candidates.size() == 1) {
            return getBean(beanFactory, candidates.get(0));
        }

        List<Candidate> localPrimaries = candidates.stream()
                .filter(Candidate::local)
                .filter(Candidate::primary)
                .toList();
        List<Candidate> effectivePrimaries = localPrimaries.isEmpty()
                ? candidates.stream().filter(Candidate::primary).toList()
                : localPrimaries;
        if (effectivePrimaries.size() == 1) {
            return getBean(beanFactory, effectivePrimaries.get(0));
        }

        throw new IllegalStateException("Coco lock manager selection is ambiguous; candidates="
                + describeCandidates(candidates)
                + ". Define exactly one CocoLockManager or mark exactly one manager as @Primary");
    }

    private static List<Candidate> candidates(ConfigurableListableBeanFactory beanFactory) {
        return Arrays.stream(BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory,
                CocoLockManager.class, true, false))
                .map(name -> candidate(beanFactory, name))
                .sorted(java.util.Comparator.comparing(Candidate::name))
                .toList();
    }

    private static Candidate candidate(ConfigurableListableBeanFactory root, String name) {
        ConfigurableListableBeanFactory owner = owner(root, name);
        boolean local = owner == root;
        boolean primary = owner.containsBeanDefinition(name)
                && owner.getMergedBeanDefinition(name).isPrimary();
        Class<?> type = owner.getType(name, false);
        return new Candidate(name, type == null ? "unknown" : type.getName(), local, primary);
    }

    private static ConfigurableListableBeanFactory owner(ConfigurableListableBeanFactory beanFactory, String name) {
        if (beanFactory.containsLocalBean(name)) {
            return beanFactory;
        }
        BeanFactory parent = beanFactory.getParentBeanFactory();
        if (parent instanceof ConfigurableListableBeanFactory configurableParent) {
            return owner(configurableParent, name);
        }
        throw new IllegalStateException("Coco lock manager candidate is not owned by a configurable bean factory: "
                + name);
    }

    private static CocoLockManager getBean(ConfigurableListableBeanFactory beanFactory, Candidate candidate) {
        return beanFactory.getBean(candidate.name(), CocoLockManager.class);
    }

    private static String describeCandidates(List<Candidate> candidates) {
        return candidates.stream()
                .map(candidate -> candidate.name() + "(" + candidate.typeName() + ")")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private record Candidate(String name, String typeName, boolean local, boolean primary) {
    }
}
