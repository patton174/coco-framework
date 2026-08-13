package io.github.coco.feature.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class CocoSchedulerAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoSchedulerAutoConfiguration.class));

    @Test void backsOffCompletelyWhenDisabled() {
        this.contextRunner.withPropertyValues("coco.scheduler.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CocoTaskSchedulerLifecycle.class);
            assertThat(context).doesNotHaveBean(CocoSchedulerAutoConfiguration.SCHEDULER_BEAN_NAME);
        });
    }

    @Test void createsDedicatedSchedulerWithoutReplacingOtherApplicationScheduler() {
        this.contextRunner.withUserConfiguration(OtherSchedulerConfiguration.class).run(context -> {
            assertThat(context).hasBean(CocoSchedulerAutoConfiguration.SCHEDULER_BEAN_NAME);
            assertThat(context).hasBean("applicationTaskScheduler");
            assertThat(context.getBean(CocoSchedulerAutoConfiguration.SCHEDULER_BEAN_NAME))
                    .isInstanceOf(ThreadPoolTaskScheduler.class);
        });
    }

    @Test void acceptsUserProvidedDedicatedScheduler() {
        this.contextRunner.withUserConfiguration(CustomCocoSchedulerConfiguration.class).run(context -> {
            assertThat(context.getBean(CocoSchedulerAutoConfiguration.SCHEDULER_BEAN_NAME))
                    .isSameAs(CustomCocoSchedulerConfiguration.SCHEDULER);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class OtherSchedulerConfiguration {
        @Bean TaskScheduler applicationTaskScheduler() { return new ThreadPoolTaskScheduler(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomCocoSchedulerConfiguration {
        static final TaskScheduler SCHEDULER = new ThreadPoolTaskScheduler();
        @Bean(name = CocoSchedulerAutoConfiguration.SCHEDULER_BEAN_NAME)
        TaskScheduler customCocoTaskScheduler() { return SCHEDULER; }
    }
}
