# Coco Compatibility Consumers

This directory is a checked-in external Maven consumer harness for the 2.x
canonical-coordinate migration. None of its fixture projects belongs to the
repository reactor or inherits `coco-framework`/`coco-parent`.

Every fixture imports `io.github.patton174:coco-dependencies` and receives the
tested version through `-Dcoco.version=<version>`. Coco dependency declarations
omit versions except for the intentional `coco-feature-web:2.0.1` mismatch.

## Full Run

Use JDK 21. Installing the current candidate first is recommended for an
unpublished version; the reactor and every fixture share only the temporary,
isolated Maven repository created by the runner.

```text
python coco-support/coco-tools/compatibility-consumers/run_compatibility_consumers.py --candidate-version 2.0.2-SNAPSHOT --install-candidate --jdk-home <jdk-21-home>
```

Omit `--install-candidate` only when the candidate is resolvable from a Maven
remote. The runner passes `-Drevision=<candidate>` to the optional reactor
install and `-Dcoco.version=<candidate>` to every candidate fixture.

The full run checks:

- identical public-FQCN source through separate canonical-only and 2.x facade-only consumers;
- all seven public feature FQCNs through canonical and old coordinates;
- `CocoTestSupport` through `coco-test-support` and `coco-test`;
- unchanged alias source from `2.0.1` to the candidate;
- unchanged class files compiled at `2.0.1` running on candidate facades;
- same-version canonical/alias mixes, source-free facades, and duplicate classes;
- real Servlet `WebApplicationContext` refreshes for canonical-only, alias-only,
  and same-version mixed candidate profiles, with exactly one feature-plan entry
  and one primary auto-configuration bean for each of the seven Coco features;
- the `coco:features` version-alignment failure for pinned Web `2.0.1`;
- Java Codegen API compilation and explicit `coco:generate` output under `target/`.

The runtime probe is test source in the standalone `feature-api` fixture. It
uses BOM-managed `spring-boot-test` and AssertJ dependencies,
`WebApplicationContextRunner`, and `@EnableAutoConfiguration`; it refreshes a
Servlet application context without starting a server. The alias-only profile
declares only old feature coordinates, while Maven resolves their canonical
implementation owners transitively. A startup failure prints the root cause,
the complete cause chain, and the stack trace before the harness fails. Each
successful refresh emits an `[EVID] COCO_RUNTIME_REGISTRATION_OK` line with the
concrete context type and the unique feature-plan and auto-configuration beans.

## Focused Validation

Fixture structure and runner unit tests require only Python:

```text
python coco-support/coco-tools/compatibility-consumers/run_compatibility_consumers.py --validate-only
python -m unittest discover -s coco-support/coco-tools/compatibility-consumers/tests -v
```

Published `2.0.1` fixtures can be exercised independently while a candidate
reactor is temporarily incomplete:

```text
python coco-support/coco-tools/compatibility-consumers/run_compatibility_consumers.py --baseline-only --jdk-home <jdk-21-home>
```

All Maven and Java invocations use argument lists with `shell=False`. Fixtures
are copied to a temporary work directory before execution, so Codegen cannot
overwrite checked-in source files.
