# Public API and ABI Compatibility Gate

## Status

- Status: accepted implementation specification
- Policy ID: `coco-public-api-compatibility`
- Baseline: published `io.github.patton174:*:2.0.1`
- Tool: `japicmp-maven-plugin` `0.23.1`
- Gate entry point: the existing `compatibility-consumers` CI job
- Required contexts: unchanged (`CI gate`, `Agent jury gate`, `Agent issue gate`)

## Policy Authority

The tracked policy bundle has four non-overlapping schema-v3 JSON authorities. Each has
an exact schema version and policy ID, and every identity-bearing array is
sorted deterministically.

1. `public-api-profile.json` is the sole authority for the 51 report-owning
   non-POM reactor artifacts and modules, 41 canonical candidate JARs, ten direct
   replacement mappings, normalized upload JAR names, baseline states, and the
   Maven `revision` candidate-version source.
2. `baseline-sha256.json` is the sole authority for the exact Maven Central
   origin, group and `2.0.1` version, 20 POM/JAR digest and size pairs, 31 exact
   POM and JAR 404 results, the signing fingerprint, and the tracked key raw
   SHA-256.
3. `allowlist.json` contains only exact incompatibility rules. It cannot define
   inventory, missing baselines, coordinates, or replacement mappings.
4. `japicmp-policy.json` defines the exact finding-key model, allowed finding
   categories, and immutable Maven-plugin and CLI tool pins.

Both consumers import the shared `policy_bundle.py` implementation. It
canonicalizes the four JSON values with recursively sorted object keys,
includes the tracked key raw digest, and recomputes one `policyBundleSha256`
from its protected checkout. The attestation records that digest but cannot
choose it. A PR-head self-test can prove only internal consistency of its own
checkout; it cannot publish this protected verdict. The obsolete externally
supplied `manifestSha256` constant is not policy.

Reports, candidate JARs, and attestations are evidence. They cannot define or
expand policy authority.

## Baseline Provenance

The trusted runner downloads only from
`https://repo.maven.apache.org/maven2`, disables environment proxies and
redirects, and does not consult a local Maven cache for provenance. For each of
the 20 published artifacts it verifies the fixed POM and JAR size and SHA-256,
HTTP `Content-Length`, complete POM coordinate, readable JAR ZIP, exact
`pom.properties`, and both detached signatures. It uses only the tracked public key and requires fingerprint
`5A99C8EF1C30294660E533E36191CBA3A67073D5`; no dynamic keyserver is trusted.

The remaining 31 artifacts must return 404 independently for both POM and JAR.
The available and missing sets are disjoint and their union must equal the
profile inventory. Baseline files and the signing key must be regular,
non-symlink files. Same-path content replacement fails by digest even when an
mtime is rolled back.

The 19 additional report owners are the current recursive reactor modules added
after the published `2.0.1` surface. Each has an explicit profile entry and
ledger record, and its Maven Central `2.0.1` POM and JAR both return `404`.
They remain report owners and must not be omitted merely because no baseline is
published.

## Report And Candidate Contract

The validator independently walks root and nested reactor POMs and requires
that inventory to equal the 51 profile entries. Every replacement target must
be a profile self-candidate. Facade chains, cycles, undeclared targets, and
candidate POM mismatches fail.

The trusted runner performs a candidate `clean install`, snapshots each of the
41 canonical JAR paths, explicit expected versions, and SHA-256 values, then
deletes and asserts absence of every legacy and run-scoped report directory. It
records the run start and input hashes and invokes the profile with `clean
verify` into one unique run ID. Exactly 51 non-empty reports must be newly
created inside that run directory and fall within the recorded time window.
Missing plugin output, empty XML, old residue, and future-dated reports fail.

The candidate producer must first prove that its built reactor contains exactly
the same 51 report-owner JARs with the profile's source path and GAV. It then
uploads exactly the 41 self-owned canonical target JARs, derived from
`comparison.targetArtifactId`, plus its non-authoritative manifest. The
protected verifier independently derives the same 41-entry set from its exact
protected profile. A facade JAR, missing canonical target, extra JAR, or a
manifest/source-path/GAV/version/SHA mismatch fails; candidate input never
chooses the trusted inventory.

After Maven completes, the runner rehashes every candidate and records the
before/after values in the attestation. The checker rehashes them again. Every
report must bind its absolute `newJar` to the profile candidate and its
`newVersion` to the explicit expected candidate version; report self-description
is never authoritative.

Every published baseline report must bind its absolute `oldJar` to the verified
Central file and report `2.0.1`. A current-only artifact must report `n.a.` if
and only if the ledger contains both 404 results. This equality is bidirectional,
so stale missing entries also fail.

## Replacement And Allowlist Contract

The eight cross-coordinate mappings compare old `2.0.1` facade JARs directly
to canonical reactor JARs: audit to audit, data-permission to data-permission,
mybatis-plus to mybatis-plus, openapi to openapi, security to security, tenant
to tenant, web to web, and `coco-test` to `coco-test-support`. The Spring
special cases compare `coco-config` and `coco-feature-runtime` directly to
`coco-spring-boot-autoconfigure`.

There is no artifact-level `REMOVED` exemption. Each allowlist rule has one
exact artifact, class, member, category, and prose reason. A class-level finding
uses the literal member `<class>`. Wildcards, package prefixes, artifact scope,
unsupported categories, duplicates, unsorted rules, and references outside the
profile fail. The only current rules are three exact `REMOVED` findings for
migrated Spring auto-configuration classes.

## Regression Proof

`run_regression_fixtures.py` compiles isolated old, broken, and repaired JARs
for four known descriptor regressions and invokes the real japicmp plugin:

1. OpenAPI auto-configuration customizer method parameter addition.
2. Observability drop-listener constructor replacement.
3. Rate-limit filter five-parameter constructor replacement.
4. Web i18n locale-resolver method parameter addition.

Each broken state must produce exactly its expected removed descriptor and each
repaired state must be clean. Current always means the exact-version reactor
`coco-openapi`, `coco-observability`, `coco-rate-limit`, or `coco-web` JAR; a
hand-written current JAR is forbidden. The OpenAPI vector also runs an unchanged
consumer compiled against published `coco-feature-openapi:2.0.1`, requiring the
broken JAR to throw the exact `NoSuchMethodError` and repaired/current JARs to
link and run.

The runner resolves published `coco-config`, `coco-feature-web`, and
`coco-feature-tenant` `2.0.1` classpaths and compiles one Java 17 consumer
against them. The classfile must contain all four exact `invokevirtual` call
sites. Without recompilation, canonical reactor JARs replace the old JARs and
the consumer directly invokes:

1. `CocoConfigAutoConfiguration.cocoFeaturePlan` with three parameters.
2. `CocoWebContextAutoConfiguration.cocoRequestParameterResolver` with two.
3. `CocoWebTraceAutoConfiguration.cocoTraceFilterRegistration` with four.
4. `CocoTenantInterceptorIgnoreGuard.beforePrepare` with the MyBatis callback.

The same consumer actually invokes unreflected MethodHandles and asserts the
published behavior. Tenant uses `getDeclaredMethod`, preventing an inherited
default no-op from passing. Before runtime, `javap -s` must find every exact
descriptor and japicmp must report no incompatibility for each direct
old-artifact-to-canonical-class comparison.

## Diagnostic And Trusted Routes

The existing compatibility job retains a read-only token and receives no
secrets. Its invocation of `run_public_api_compatibility.py` is an untrusted
PR-head diagnostic/self-test only. It catches report, closure, provenance, and
runtime regressions but cannot make PR-edited policy or checker code protected
authority.

The staged dormant shadow route identified by `04c47a` owns the separately
reviewed protected-base checker and PR-artifact two-phase design. Only after
that route is integrated and cut over may its base-bound result become the
protected verdict. This stack does not duplicate that route or add a same-job
base checkout. `pull_request_target` never executes PR code, and required
context names remain unchanged.

## Deferred Hardening

This implementation requires the reactor source checkout to remain within the
documented controlled path-length threshold. Supporting arbitrarily deep source
checkouts and adding threat cases beyond the tracked path, reparse-point, and
process-staging tests are deferred to a later protected-route change. They must
not weaken the current fail-closed checks when implemented.

## Verification

```text
python coco-support/coco-tools/public-api-compatibility/run_public_api_compatibility.py --candidate-version 2.0.2-SNAPSHOT --protected-sha <protected-40-hex-sha> --jdk-home <jdk-21-home> --work-directory <new-path>
python coco-support/coco-tools/public-api-compatibility/run_regression_fixtures.py --candidate-version 2.0.2-SNAPSHOT --jdk-home <jdk-21-home>
python -B -m unittest discover -s coco-support/coco-tools/public-api-compatibility/tests -v
```

The Maven profile remains opt-in and is not a standalone CI entry. The current
CI diagnostic invokes the attested runner, verifies its attestation exists,
then runs exact-version regression fixtures. The runner itself performs both
required clean Maven phases and its self-consistency checker call. Protected
enforcement remains dormant until the `04c47a` route cutover.
