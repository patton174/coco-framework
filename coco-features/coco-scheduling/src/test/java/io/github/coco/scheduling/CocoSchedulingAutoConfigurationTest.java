package io.github.coco.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.aop.framework.ProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

class CocoSchedulingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoSchedulingAutoConfiguration.class));

    @Test
    void registersAnnotatedMethodUsingCustomSchedulerGuardAndObserver() {
        this.contextRunner.withUserConfiguration(AnnotatedConfiguration.class).run(context -> {
            ManualTaskScheduler springScheduler = context.getBean(ManualTaskScheduler.class);
            CocoTaskScheduler scheduler = context.getBean(CocoTaskScheduler.class);
            CountingGuard guard = context.getBean(CountingGuard.class);
            CapturingObserver observer = context.getBean(CapturingObserver.class);
            AnnotatedTasks tasks = context.getBean(AnnotatedTasks.class);

            assertThat(context).hasSingleBean(CocoTaskExecutionGuard.class);
            assertThat(context).hasBean("cocoSchedulingMessageBundleRegistrar");
            assertThat(scheduler.list()).extracting(CocoTaskStatus::name).containsExactly("annotatedTasks#run");
            springScheduler.latest().run();

            assertThat(tasks.executions).hasValue(1);
            assertThat(guard.acquisitions).hasValue(1);
            assertThat(observer.events).extracting(CocoTaskExecutionEvent::outcome)
                    .containsExactly(CocoTaskExecutionOutcome.STARTED, CocoTaskExecutionOutcome.SUCCEEDED);
        });
    }

    @Test
    void rejectsInvalidAnnotationConfigurationAtStartup() {
        this.contextRunner.withUserConfiguration(InvalidAnnotatedConfiguration.class).run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
            assertThat(context.getStartupFailure()).isInstanceOf(CocoSchedulingException.class);
        });
    }

    @Test
    void disablesEntireSchedulingInfrastructure() {
        this.contextRunner.withPropertyValues("coco.scheduling.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CocoTaskScheduler.class);
            assertThat(context).doesNotHaveBean(CocoTaskExecutionGuard.class);
            assertThat(context).doesNotHaveBean("cocoScheduledTaskRegistrar");
        });
    }

    @Test
    void respectsCustomCocoTaskSchedulerBean() {
        this.contextRunner.withUserConfiguration(CustomApiConfiguration.class).run(context -> {
            RecordingScheduler scheduler = context.getBean(RecordingScheduler.class);

            assertThat(context.getBean(CocoTaskScheduler.class)).isSameAs(scheduler);
            assertThat(scheduler.registered).extracting(CocoTaskDefinition::getName).containsExactly("annotatedTasks#run");
        });
    }

    @Test
    void registersAnnotatedGenericTargetThroughJdkProxyOnlyOnce() {
        this.contextRunner.withUserConfiguration(JdkProxyConfiguration.class).run(context -> {
            ManualTaskScheduler springScheduler = context.getBean(ManualTaskScheduler.class);
            CocoTaskScheduler scheduler = context.getBean(CocoTaskScheduler.class);
            GenericScheduledTargetHolder holder = context.getBean(GenericScheduledTargetHolder.class);

            assertThat(scheduler.list()).extracting(CocoTaskStatus::name)
                    .containsExactly("genericScheduledProxy#run");
            assertThat(springScheduler.entries()).hasSize(1);
            springScheduler.latest().run();

            assertThat(holder.target.executions).hasValue(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedConfiguration {

        @Bean
        ManualTaskScheduler taskScheduler() {
            return new ManualTaskScheduler();
        }

        @Bean
        CountingGuard cocoTaskExecutionGuard() {
            return new CountingGuard();
        }

        @Bean
        CapturingObserver capturingObserver() {
            return new CapturingObserver();
        }

        @Bean
        AnnotatedTasks annotatedTasks() {
            return new AnnotatedTasks();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidAnnotatedConfiguration {

        @Bean
        ManualTaskScheduler taskScheduler() {
            return new ManualTaskScheduler();
        }

        @Bean
        InvalidAnnotatedTasks invalidAnnotatedTasks() {
            return new InvalidAnnotatedTasks();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomApiConfiguration {

        @Bean
        CocoTaskScheduler cocoTaskScheduler() {
            return new RecordingScheduler();
        }

        @Bean
        AnnotatedTasks annotatedTasks() {
            return new AnnotatedTasks();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdkProxyConfiguration {

        @Bean
        ManualTaskScheduler taskScheduler() {
            return new ManualTaskScheduler();
        }

        @Bean
        GenericScheduledTargetHolder genericScheduledTargetHolder() {
            return new GenericScheduledTargetHolder();
        }

        @Bean
        GenericScheduledContract<?> genericScheduledProxy(GenericScheduledTargetHolder holder) {
            ProxyFactory proxyFactory = new ProxyFactory(holder.target);
            proxyFactory.setInterfaces(GenericScheduledContract.class);
            return (GenericScheduledContract<?>) proxyFactory.getProxy();
        }
    }

    static class AnnotatedTasks {

        private final AtomicInteger executions = new AtomicInteger();

        @CocoScheduled(fixedRate = "1s")
        void run() {
            this.executions.incrementAndGet();
        }
    }

    static class InvalidAnnotatedTasks {

        @CocoScheduled(fixedDelay = "1s", fixedRate = "1s")
        void invalid() {
        }
    }

    interface GenericScheduledContract<T> {

        T run();
    }

    static final class GenericScheduledTarget implements GenericScheduledContract<String> {

        private final AtomicInteger executions = new AtomicInteger();

        @Override
        @CocoScheduled(fixedRate = "1s")
        public String run() {
            this.executions.incrementAndGet();
            return "done";
        }
    }

    static final class GenericScheduledTargetHolder {

        private final GenericScheduledTarget target = new GenericScheduledTarget();
    }

    static final class CountingGuard implements CocoTaskExecutionGuard {

        private final AtomicInteger acquisitions = new AtomicInteger();

        @Override
        public boolean tryAcquire(String taskName) {
            this.acquisitions.incrementAndGet();
            return true;
        }

        @Override
        public void release(String taskName) {
        }
    }

    static final class CapturingObserver implements CocoTaskExecutionObserver {

        private final List<CocoTaskExecutionEvent> events = new ArrayList<>();

        @Override
        public void onExecution(CocoTaskExecutionEvent event) {
            this.events.add(event);
        }
    }

    static final class RecordingScheduler implements CocoTaskScheduler {

        private final List<CocoTaskDefinition> registered = new ArrayList<>();

        @Override
        public void register(CocoTaskDefinition definition) {
            this.registered.add(definition);
        }

        @Override
        public void replace(CocoTaskDefinition definition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(String name) {
            return false;
        }

        @Override
        public List<CocoTaskStatus> list() {
            return List.of();
        }

        @Override
        public Optional<CocoTaskStatus> status(String name) {
            return Optional.empty();
        }

        @Override
        public void close() {
        }
    }
}
