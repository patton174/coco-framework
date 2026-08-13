package io.github.coco.feature.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoLockManagerResolverTest {

    @BeforeEach
    void resetCounters() {
        CountingManager.instances.set(0);
        UntypedManagerFactory.instances.set(0);
        UntypedManagerFactory.products.set(0);
        TypedManagerFactory.instances.set(0);
        TypedManagerFactory.products.set(0);
    }

    @Test
    void beanNamedLikeFormerParameterDoesNotWinWithoutPrimary() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        registerManager(factory, "lockManagers", false, false);
        registerManager(factory, "secondary", false, false);

        assertThatThrownBy(() -> CocoLockManagerResolver.resolve(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lockManagers(")
                .hasMessageContaining("secondary(")
                .hasMessageNotContaining("manager-secret");
        assertThat(CountingManager.instances).hasValue(0);
    }

    @Test
    void lazyPrimaryIgnoresUntypedLazyFactoryWithoutInstantiatingOtherCandidates() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        registerManager(factory, "primaryManager", true, true);
        registerManager(factory, "secondaryManager", false, true);
        RootBeanDefinition unknownFactory = new RootBeanDefinition(UntypedManagerFactory.class);
        unknownFactory.setLazyInit(true);
        factory.registerBeanDefinition("unknownManagerFactory", unknownFactory);

        CocoLockManager selected = CocoLockManagerResolver.resolve(factory);

        assertThat(selected).isInstanceOf(CountingManager.class);
        assertThat(CountingManager.instances).hasValue(1);
        assertThat(UntypedManagerFactory.instances).hasValue(0);
        assertThat(UntypedManagerFactory.products).hasValue(0);
        assertThat(factory.containsSingleton("secondaryManager")).isFalse();
    }

    @Test
    void typedFactoryBeanCanBeSelectedAsPrimaryFromMetadata() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        registerManager(factory, "secondaryManager", false, true);
        RootBeanDefinition typedFactory = new RootBeanDefinition(TypedManagerFactory.class);
        typedFactory.setLazyInit(true);
        typedFactory.setPrimary(true);
        factory.registerBeanDefinition("typedPrimaryFactory", typedFactory);

        CocoLockManager selected = CocoLockManagerResolver.resolve(factory);

        assertThat(selected).isInstanceOf(CountingManager.class);
        assertThat(TypedManagerFactory.instances).hasValue(1);
        assertThat(TypedManagerFactory.products).hasValue(1);
        assertThat(CountingManager.instances).hasValue(1);
        assertThat(factory.containsSingleton("secondaryManager")).isFalse();
    }

    @Test
    void localPrimaryWinsOverParentPrimary() {
        try (AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
                AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext()) {
            registerManager(parent, "parentPrimary", true, true);
            parent.refresh();
            child.setParent(parent);
            registerManager(child, "childPrimary", true, true);
            child.refresh();

            CocoLockManager selected = CocoLockManagerResolver.resolve(child.getBeanFactory());

            assertThat(selected).isSameAs(child.getBean("childPrimary"));
            assertThat(parent.getBeanFactory().containsSingleton("parentPrimary")).isFalse();
        }
    }

    @Test
    void childBeanNameOverridesSameNamedParentCandidate() {
        try (AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
                AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext()) {
            registerManager(parent, "sharedManager", true, true);
            parent.refresh();
            child.setParent(parent);
            child.registerBean("sharedManager", NamedManager.class, () -> new NamedManager("child"),
                    definition -> definition.setLazyInit(true));
            child.refresh();

            assertThat(CocoLockManagerResolver.resolve(child.getBeanFactory()))
                    .isSameAs(child.getBean("sharedManager"));
            assertThat(parent.getBeanFactory().containsSingleton("sharedManager")).isFalse();
        }
    }

    private static void registerManager(DefaultListableBeanFactory factory, String name, boolean primary,
            boolean lazy) {
        RootBeanDefinition definition = new RootBeanDefinition(CountingManager.class);
        definition.setPrimary(primary);
        definition.setLazyInit(lazy);
        factory.registerBeanDefinition(name, definition);
    }

    private static void registerManager(AnnotationConfigApplicationContext context, String name, boolean primary,
            boolean lazy) {
        context.registerBean(name, CountingManager.class, CountingManager::new, definition -> {
            definition.setPrimary(primary);
            definition.setLazyInit(lazy);
        });
    }

    static class CountingManager implements CocoLockManager {
        static final AtomicInteger instances = new AtomicInteger();

        CountingManager() {
            instances.incrementAndGet();
        }

        @Override public Optional<CocoLock> tryLock(String key, Duration waitTime, Duration leaseTime) {
            return Optional.empty();
        }
        @Override public void close() { }
        @Override public String toString() { return "manager-secret"; }
    }

    static final class NamedManager extends CountingManager {
        private final String name;

        NamedManager(String name) {
            this.name = name;
        }

        @Override public String toString() { return this.name; }
    }

    static final class UntypedManagerFactory implements FactoryBean<Object> {
        static final AtomicInteger instances = new AtomicInteger();
        static final AtomicInteger products = new AtomicInteger();

        UntypedManagerFactory() {
            instances.incrementAndGet();
        }

        @Override public Object getObject() {
            products.incrementAndGet();
            return new CountingManager();
        }
        @Override public Class<?> getObjectType() { return null; }
    }

    static final class TypedManagerFactory implements FactoryBean<CocoLockManager> {
        static final AtomicInteger instances = new AtomicInteger();
        static final AtomicInteger products = new AtomicInteger();

        TypedManagerFactory() {
            instances.incrementAndGet();
        }

        @Override public CocoLockManager getObject() {
            products.incrementAndGet();
            return new CountingManager();
        }
        @Override public Class<?> getObjectType() { return CocoLockManager.class; }
    }
}
