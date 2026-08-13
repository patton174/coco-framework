package io.github.coco.context.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.github.coco.context.CocoContextSnapshot;
import io.github.coco.context.CocoContextSnapshotContributor;
import io.github.coco.context.CocoContextSnapshotFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskDecorator;

class CocoContextSpringAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CocoContextSpringAutoConfiguration.class);

    @Test
    void createsDefaultDecoratorAndContributors() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskDecorator.class);
            assertThat(context).hasSingleBean(CocoContextSnapshotFactory.class);
            assertThat(context.getBeansOfType(CocoContextSnapshotContributor.class)).hasSize(3);
        });
    }

    @Test
    void backsOffForBusinessFactoryOrTaskDecorator() {
        contextRunner.withBean(CocoContextSnapshotFactory.class,
                () -> new CocoContextSnapshotFactory(List.of())).run(context ->
                        assertThat(context.getBeansOfType(TaskDecorator.class)).isEmpty());
        contextRunner.withBean(TaskDecorator.class, () -> runnable -> runnable).run(context ->
                assertThat(context).doesNotHaveBean(CocoContextTaskDecorator.class));
    }

    @Test
    void disabledPropertyCreatesNoBeans() {
        contextRunner.withPropertyValues("coco.context.propagation.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CocoContextSnapshotFactory.class);
            assertThat(context.getBeansOfType(TaskDecorator.class)).isEmpty();
        });
    }

    @Test
    void customContributorParticipatesWithoutSuppressingDefaults() {
        contextRunner.withBean(CocoContextSnapshotContributor.class, () -> new CocoContextSnapshotContributor() {
            @Override public String id() { return "custom"; }
            @Override public CocoContextSnapshot capture() { return CocoContextSnapshot.noop(); }
        }).run(context -> assertThat(context.getBeansOfType(CocoContextSnapshotContributor.class)).hasSize(4));
    }
}
