package io.github.coco.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Coco 上下文快照组合捕获器。
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoContextSnapshotFactory {

    private final List<CocoContextSnapshotContributor> contributors;

    public CocoContextSnapshotFactory(Iterable<? extends CocoContextSnapshotContributor> contributors) {
        Objects.requireNonNull(contributors, "contributors must not be null");
        List<CocoContextSnapshotContributor> collected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (CocoContextSnapshotContributor contributor : contributors) {
            CocoContextSnapshotContributor checked = Objects.requireNonNull(contributor, "contributor must not be null");
            String id = Objects.requireNonNull(checked.id(), "contributor id must not be null");
            if (id.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException("Coco context snapshot contributor id must be unique and non-blank: " + id);
            }
            collected.add(checked);
        }
        collected.sort(Comparator.comparingInt(CocoContextSnapshotContributor::order)
                .thenComparing(CocoContextSnapshotContributor::id));
        this.contributors = List.copyOf(collected);
    }

    public CocoContextSnapshot capture() {
        List<CocoContextSnapshot> snapshots = new ArrayList<>(this.contributors.size());
        for (CocoContextSnapshotContributor contributor : this.contributors) {
            snapshots.add(Objects.requireNonNull(contributor.capture(), "contributor capture must not return null"));
        }
        return CocoContextSnapshot.compose(snapshots);
    }
}
