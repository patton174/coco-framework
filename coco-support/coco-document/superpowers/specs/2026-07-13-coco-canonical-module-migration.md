# Coco Canonical Module Migration Specification

## Status And Scope

- Roadmap: 8-prep metadata/plugin/governance, `[2.0 8a/9]` persistence,
  `[2.0 8b/9]` platform, `[2.0 8c/9]` Web, and 9a/9b closure.
- Published compatibility floor: `v2.0.1`.
- Scope: Maven artifact, directory, and implementation ownership only.

`v2.0.1` published the old coordinates. They remain resolvable and compatible
throughout 2.x; removal is a 3.0 decision. This supersedes earlier plans that
assumed the old coordinates could be deleted before a public 2.0 release.

## Mapping

| Batch | Published 2.x coordinate | Canonical implementation |
| --- | --- | --- |
| 8a | `coco-feature-mybatis-plus` | `coco-mybatis-plus` |
| 8a | `coco-feature-tenant` | `coco-tenant` |
| 8a | `coco-feature-data-permission` | `coco-data-permission` |
| 8b | `coco-feature-audit` | `coco-audit` |
| 8b | `coco-feature-security` | `coco-security` |
| 8b | `coco-feature-openapi` | `coco-openapi` |
| 8c | `coco-feature-web` | `coco-web` |
| 9 | `coco-test` | `coco-test-support` |

The group ID and project version remain identical. Canonical feature modules
live under `coco-features/coco-*`; test support lives under
`coco-support/coco-test-support`. Old coordinates move under
`coco-build/coco-compatibility`.

## Compatibility Contract

- The canonical artifact is the sole owner of production classes, resources,
  behavior tests, Spring registrations, metadata, messages, service files, and
  native hints. Files move; implementation is never copied.
- Every old artifact keeps its GAV and JAR packaging, contains no main source or
  implementation resources, and has one non-optional compile dependency on the
  same-version canonical artifact. A test-only public-FQCN compile probe is
  allowed. Relocation or POM-only packaging is not used in this migration.
- Root dependency management and `coco-dependencies` manage both coordinate
  forms. The starter, feature modules, samples, and other repository consumers
  use canonical feature coordinates only; aliases exist for external 2.x
  compatibility.
- Java packages, public FQCNs and signatures, annotations, SPI, bean names,
  conditions, ordering, configuration keys/defaults, auto-configuration class
  names, message basenames, manifest schema, and stable feature IDs do not
  change.
- The `coco-maven-plugin` coordinate, `coco` prefix, goals, and parameters stay
  compatible. `coco-feature-codegen`, `CODEGEN`, and `coco:generate` are not
  renamed or removed by these batches.
- Published record components and constructor descriptors do not change. Alias
  support uses internal/static metadata or additive methods, never a new
  `CocoFeatureDefinition` record component.

## Feature And Packaging Rules

New manifests and dependency injection use canonical artifact IDs. Each renamed
feature also declares its old ID as an equivalent prune alias. Existing
MyBatis-Plus transitive prune IDs remain.

The build plugin must:

1. treat an existing canonical or old Coco coordinate as satisfying the feature
   dependency, while injecting the canonical coordinate when neither exists;
2. avoid relying on the `coco-feature-` prefix for Coco artifact ownership;
3. remove canonical and old forms from the Maven model, resolved artifacts,
   `BOOT-INF/lib`, `BOOT-INF/classpath.idx`, and `BOOT-INF/layers.idx`; and
4. combine aliases from current feature metadata with aliases stored in an old
   valid manifest, so a `v2.0.1` manifest can prune canonical artifacts.

Canonical-only, old-only, and mixed tests must prove one implementation and no
duplicate auto-configuration.

## Review Batches

- **8-prep metadata:** switch feature metadata and its tests to canonical IDs
  plus old aliases. This is separate because the existing Feature Model policy
  already consumes almost all protected context.
- **8-prep plugin:** update dependency detection, model pruning, old-manifest
  alias union, archive pruning, and tests without mixing module moves.
- **8-prep governance:** make CI archive assertions and labels understand old
  and canonical paths before source moves; do not mix workflow policy changes
  into the Web source PR.
- **8a persistence:** move MyBatis-Plus, tenant, and data-permission ownership;
  tenant and data-permission depend on canonical MyBatis-Plus.
- **8b platform:** move audit, security, and OpenAPI ownership. Stable feature
  graph dependencies remain unchanged.
- **8c Web:** move all Web and replay-store ownership, including the `.gitignore`
  exception for the Java `context/target` package. The starter then uses all
  seven canonical feature artifacts.
- **9a support:** move test support, retain `coco-test` as a facade, and switch
  codegen/security tests.
- **9b closure:** move `coco-config` and `coco-feature-runtime` facades under
  compatibility, run final ownership scans, and change no public coordinate.

Each batch keeps the mixed reactor buildable and may be split further only at a
source-free facade boundary when a protected review budget requires it.

## Deferred Cross-Repository Moves

`coco-feature-codegen`, its API, templates, and `coco:generate` remain until a
versioned cross-repository migration proves a released `coco-generate` receiver
with equivalent consumers. `coco-samples` remains until protected `coco-admin`
CI continuously replaces basic/full starter, pruning, archive, and business-flow
verification. A one-off run is insufficient.

## Required Evidence

Use JDK 21 and Java 17 bytecode. Every sub-batch runs focused tests, a clean
reactor verify, governance tests, README drift checks, and `codegraph sync .`
after source moves. Final closure also runs release-profile smoke with GPG
skipped and both sample business-flow checks.

Checked-in consumers outside the reactor must prove:

- unchanged source upgrades from old 2.0.1 coordinates by changing only the
  version;
- binaries compiled against 2.0.1 run on candidate facades without recompiling;
- canonical, old, BOM-managed, starter, and mixed dependencies compile
  representative existing FQCNs and start with one registration;
- old and canonical test-support consumers compile `io.github.coco.test.*`;
- plugin dependency injection and pruning work for canonical, old, mixed, and
  old-manifest inputs; and
- existing Codegen Java use and `coco:generate` still work.

Same-version old/canonical mixes are supported. A candidate canonical artifact
combined with an explicitly pinned old `2.0.1` implementation must fail the
build with a version-alignment diagnostic; it must never package duplicate
FQCNs or registrations silently.

Inspect built main/source/Javadoc artifacts and dependency trees: canonical JARs
own implementation, compatibility JARs do not, both resolve at one version, and
starter/sample archives contain no old feature aliases. Update aligned README
fragments and render generated READMEs; never edit generated READMEs directly.

Each PR stays below `180000` diff characters, `48000` protected policy
characters, `8000` intent characters, and the configured code-context limits.
Required policy or tests are never omitted to fit a budget. All protected gates,
current human approval, resolved conversations, and merge-commit flow remain
mandatory.

## Acceptance Criteria

- Seven canonical features and `coco-test-support` are the only implementation
  owners; all eight old coordinates remain source-free 2.x compatibility JARs.
- BOM and root management contain both forms; internal production composition
  uses canonical features, except unchanged `coco-feature-codegen`.
- Public Java, configuration, feature, manifest, plugin, and runtime behavior
  remain compatible.
- Injection, pruning, external consumers, clean reactor/release/sample builds,
  CodeGraph ownership checks, README checks, and governance tests pass.
- Codegen and samples remain until their explicit replacement gates pass.
