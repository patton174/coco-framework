# Artifact Ownership Checker

This stdlib-only Python checker validates the publication boundary introduced by
the canonical module migration. It reads versioned JARs directly from each
module's `target` directory and reports all violations in one run.

The checker covers these artifact groups:

- Canonical owners: `coco-spring-boot-autoconfigure`, `coco-web`,
  `coco-mybatis-plus`, `coco-tenant`, `coco-data-permission`, `coco-audit`,
  `coco-openapi`, `coco-security`, and `coco-test-support`.
- Legacy facades: `coco-config`, `coco-feature-runtime`, the seven
  `coco-feature-*` artifacts, and `coco-test` under
  `coco-build/coco-compatibility`.

Every required JAR must be a readable ZIP archive with valid member CRCs.
Canonical main JARs must contain their configured representative implementation
class. Legacy main JARs may contain only normal JAR/Maven metadata: `.class`
files, Spring auto-configuration metadata, messages, and any other implementation
resource fail the check.

## Standard Build

From the repository root, run the normal build and then check main JARs using
the exact Maven revision:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B -Drevision=2.0.2-SNAPSHOT verify
python coco-support/coco-tools/artifact-ownership/check_artifact_ownership.py `
  --version 2.0.2-SNAPSHOT
```

## Release Smoke

The release profile attaches source and Javadoc artifacts. After the documented
release smoke build, require those attached JARs as well:

```powershell
mvn -B -Prelease -Drevision=1.0.0 -Dgpg.skip=true -DskipTests verify
python coco-support/coco-tools/artifact-ownership/check_artifact_ownership.py `
  --version 1.0.0 `
  --require-attached
```

In attached mode, canonical sources and Javadocs must contain the representative
type, legacy sources must contain no `.java` entries, and legacy Javadocs must
contain no type pages.

The repository root is located automatically from the script path, so the
checker does not depend on the current working directory. Use
`--repository-root PATH` to inspect a different checkout. Exit status is `0`
for success, `1` for ownership violations, and `2` for invocation or repository
location errors.

## Tests

The unit tests create temporary ZIP files and do not require a prior Maven build:

```powershell
python -m unittest discover `
  -s coco-support/coco-tools/artifact-ownership/tests `
  -v
```
