# Coco Canonical Coordinate Review Contract

Published `v2.0.1` coordinates remain source-free 2.x JAR facades over
same-version canonical artifacts. Canonical artifacts own implementation; BOM
manages both and internal consumers use canonical coordinates.

Only Maven ownership changes. API, behavior, configuration, feature graph,
auto-configuration, manifests, and plugin goals stay compatible. Emit canonical
IDs plus old prune aliases, union old-manifest aliases, and do not assume a
`coco-feature-` prefix.

Prove old, canonical, and mixed consumers start once, pruning removes artifacts
and Boot-index entries, and facades are empty. Behavior specs remain authoritative.
