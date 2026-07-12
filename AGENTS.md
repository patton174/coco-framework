# Coco Framework Agent Guide

## Project Positioning

Coco Framework is a high-convention Spring Boot Web server framework for rapidly building production-ready Web services.

It is not limited to SaaS systems, and it is not a zero-code business runtime. The framework should provide a strong black-box foundation for repeated infrastructure concerns while leaving business code, domain design, generated CRUD code, custom queries, and transaction boundaries under developer control.

## Core Principles

- Prefer top-level encapsulation for repeated Web server infrastructure: starter wiring, feature activation, build-time pruning, runtime conditions, unified responses, exceptions, i18n, trace context, access logs, request security, audit pipeline, MyBatis-Plus integration, tenant SQL isolation, and data-permission SQL integration.
- Avoid over-encapsulation of business behavior: do not hide CRUD behind runtime magic, do not expose entities automatically as APIs, and do not force one user/role/menu/tenant model onto every application.
- CRUD and standard business scaffolding should be generated as readable source code through code generation, then owned by the business project.
- Annotations should express clear business or framework intent. They must not become opaque switches that make behavior hard to debug.
- Defaults should be useful and safe, but every major integration point should remain configurable or replaceable through properties, beans, or SPI.
- Business developers are expected to understand normal Java, Spring Boot, Maven, and the generated code they keep. Coco should reduce boilerplate, not remove engineering ownership.

## Ecosystem Boundaries

- `coco-framework` is the independent Web server foundation. It must not depend on `coco-admin` or `coco-generate`.
- `coco-admin` is an ERP product built on the framework. ERP domains, authentication choices, organization and permission models, workflows, reports, and transactions belong there.
- `coco-generate` is a development-time source generator and template platform. Generated source may target Coco applications, but business applications must not require the generator at runtime.
- `coco-admin` may use `coco-generate` during development; generated files are then reviewed, committed, and owned by the Admin repository.
- Existing public Codegen APIs and the `coco:generate` goal remain supported until a separately reviewed compatibility migration moves implementation ownership. Do not remove or duplicate them without a versioned migration path.

## Current Architecture

- `coco-parent` is the recommended parent POM for business applications. It imports the BOM, runs Spring Boot repackage, runs `coco:features`, and runs `coco:prune-package`.
- `coco-spring-boot-starter` is the single normal starter dependency for applications. It brings common infrastructure and standard feature modules.
- `coco-api` contains stable public contracts such as `CocoConfigurer`, `CocoFeature`, `CocoFeatureRegistry`, and `@CocoFeatures`.
- `coco-context`, `coco-exception`, `coco-i18n`, and `coco-logging` contain reusable foundation infrastructure.
- `coco-config` binds `coco.*` configuration and computes the final runtime feature plan.
- `coco-feature-model` owns standard feature metadata, dependencies, manifest model, and feature resolution.
- `coco-feature-runtime` filters auto-configuration by feature state.
- `coco-feature-web` owns Servlet Web integration: response wrapping, exception response handling, trace, request context, access logging, signatures, encryption, process-local replay protection, and an explicitly selected JDBC replay-store reference implementation.
- `coco-feature-mybatis-plus` owns MyBatis-Plus interceptors, pagination, and SQL guard integration.
- `coco-feature-tenant` owns tenant context and MyBatis-Plus tenant SQL isolation.
- `coco-feature-data-permission` owns data permission context, resource mapping, and MyBatis-Plus data-permission SQL conditions.
- `coco-feature-audit` provides the audit event pipeline, default structured logging, formatter and recorder SPI; `coco-feature-openapi` adapts Coco metadata to SpringDoc when present.
- `coco-feature-codegen` provides a replaceable template generator, built-in explicit CRUD source templates, and safe generated-file writing; `coco-maven-plugin` exposes this through the opt-in `coco:generate` goal.
- `coco-maven-plugin` creates `META-INF/coco/features.json`, applies enabled feature dependencies, and prunes disabled feature artifacts from Spring Boot packages.
- `coco-support/coco-document` contains repository architecture, release, audit, plan, and specification documents; `coco-support/coco-tools` contains development-only repository tools; `coco-support/coco-test` contains reusable test support.
- During the staged 2.0 migration, I18n, Logging, and Feature Model retain their existing auto-configuration FQCNs. Their Spring bindings move to `coco-spring-boot-autoconfigure` in the separately reviewed Spring composition batch.

## Development Rules

- If `.codegraph/` exists, use CodeGraph before grep/find or broad file reads when understanding or locating code.
- After source code changes, run `codegraph sync .` automatically so the local code graph stays current before follow-up exploration.
- Keep changes scoped to the requested feature, module, or document.
- Match existing Maven module boundaries and Java package names.
- Keep public APIs small and stable. Add SPI only when there is a real replacement point.
- Preserve business-facing simplicity: one starter, declarative configuration, clear override points.
- Prefer generated source code for business CRUD over runtime dynamic controllers.
- Avoid adding feature behavior to `coco-spring-boot-starter`; starters should compose capabilities, not own behavior.
- Do not make common modules depend on concrete feature modules.
- Use standard JavaDoc HTML tags for public classes. Existing project docs and JavaDoc use Chinese descriptions; continue that style where appropriate.
- Keep message text in module message bundles instead of hard-coding user-visible text.
- Do not introduce unrelated refactors, formatting churn, or generated build artifacts into commits.
- Name GitHub Actions files with lowercase kebab-case; prefix reusable-only workflows with `reusable-`. Keep Python automation in snake_case and Node.js automation in lowercase kebab-case. Workflow file renames must preserve stable required-check contexts.

## README Maintenance

- `README.md` and `README_CN.md` are deterministic generated documents. Do not edit them directly.
- Maintain English and Chinese source fragments under `.github/readme/fragments/`; the Chinese document is the structural and visual reference, and both manifests must remain aligned.
- Run `node .github/readme/scripts/render.mjs --write` after fragment changes and `node .github/readme/scripts/render.mjs --check` before committing.
- Architecture and explanatory fragments may be updated by the protected, low-frequency README Agent workflow. Stars, forks, contributor data, and timestamps remain script-owned and must not be model-generated.
- README automation must rebuild its branch from protected `main`, open a PR, and pass the normal protected merge path. It must never execute scripts from an existing automation branch with repository secrets.

## Verification

Use JDK 21. The Maven compiler target is Java 17.

Common commands:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn verify
```

Focused module verification:

```powershell
mvn -pl :module-artifact-id -am test
```

Sample verification:

```powershell
mvn -B install
mvn -B -f coco-samples/coco-sample-basic/pom.xml verify
python coco-samples/coco-sample-basic/scripts/verify_business_flow.py
```

Release profile smoke check without local GPG:

```powershell
mvn -B -Prelease -Drevision=1.0.0 -Dgpg.skip=true -DskipTests verify
```

## Git Workflow

- Work on `codex-dev-*` or focused feature branches, then merge through PR into `main`.
- `main` is protected. Direct pushes, force pushes, and branch deletion are not part of the development flow.
- Keep `main` release-ready and require the stable `CI gate`, `Agent jury gate`, and `Agent issue gate` contexts before merging. `CI gate` aggregates the cross-platform test matrix, static analysis, governance protocol tests, README drift validation, and CodeQL; do not protect matrix-generated check names individually.
- Bind all three required status contexts to the built-in GitHub Actions App ID `15368`; a same-name status from another provider must not satisfy branch protection.
- Enforce branch protection for administrators, require the current CODEOWNER approval, dismiss stale approvals after every head change, and resolve all review conversations before merge.
- GitHub Actions may use only GitHub-owned actions pinned to immutable commit SHAs. The default workflow token is read-only, and every external-fork workflow run requires maintainer approval.
- Agent review uses five independent specialists, two independent verifiers, and one chair. P0/P1 findings block only when both verifiers agree; the chair cannot change the deterministic result.
- Agent secret routing and Dependabot deferral follow the normative [multi-agent jury specification](coco-support/coco-document/superpowers/specs/2026-07-10-multi-agent-review-jury.md) and [governance automation specification](coco-support/coco-document/superpowers/specs/2026-07-11-agent-governance-automation.md). Keep every route fail-closed: PR-head code never receives secrets or executes, and bot review never replaces the required current-head human approval. Update the specifications before changing the route protocol.
- Trusted Agent-review runtime changes cannot self-host their PR-head code under `pull_request_target`. If a confirmed protected-base defect prevents one required Agent context from succeeding, record the incident in an Issue, require `CI gate`, exact-head protocol tests, current App-authored PR approval, independent review, and resolved conversations, then temporarily remove only the broken required context. Merge the exact reviewed head through the PR as a merge commit, restore that App-bound context immediately, and run same-repository and no-secret canaries before allowing further merges. Never disable other protections, push directly to `main`, or execute PR-head reviewer code with repository secrets.
- The Agent workflow publishes gate statuses through the GitHub Actions App. A dedicated Coco GitHub App publishes the managed jury comment and PR-bound finding Issues; it does not submit GitHub approvals. Every open Issue carrying the protected `coco-agent-review` marker blocks `Agent issue gate` until a later bound review or maintainer resolution closes it.
- Automatic merge uses the dedicated App only after re-reading the exact head and confirming all three gates, one current human approval, zero unresolved review conversations, zero open bound Agent Issues, a conflict-free and up-to-date mergeable branch, and merge-commit-only repository settings. GitHub REST `mergeable_state` may be `clean` or `unstable`; `unstable` only tolerates optional status noise and never replaces the explicit required-gate checks. Reject `behind`, `blocked`, `dirty`, `draft`, and `unknown`, and never use an administrator bypass for a missing condition.
- Require one current human approval and resolve review conversations before merging. Use merge commits so roadmap PR boundaries remain visible; squash and rebase merging are disabled at repository level.
- Maintainer-authored changes must use a same-repository `codex/*` branch and the protected `Open Agent Pull Request` workflow, so the dedicated App owns the PR and the maintainer can supply the required human approval.
- Repository automations that update tracked files must use a PR and the protected CI workflow rather than pushing directly to `main`.
- Use SemVer release tags such as `v1.0.2`.
- Release tags matching `v*` are immutable after creation: the active repository ruleset blocks tag updates and deletion.
- Releases must be manually dispatched from the latest protected `main` commit. The Release workflow waits for Maven Central state `PUBLISHED`, then tags the exact validated dispatch SHA; do not push release tags manually.
- The `coco-agent` and `coco-spring` environments accept only the exact `main` branch. Administrator environment bypass is disabled for both, and `coco-spring` requires maintainer deployment approval before release secrets become available.
- GitHub native auto-merge is disabled. The dedicated Coco App workflow is the only automated merge path and must re-check every protected condition against the exact PR head before merging.
- Fetch and push use the HTTPS remote through the GitHub CLI credential helper so proxy-enabled environments do not depend on SSH transport.
- Before reporting completion, check `git status -sb` and mention any untracked or ignored output only if it matters.
