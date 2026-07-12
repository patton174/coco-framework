# Coco Framework Governance

[English](#english) | [简体中文](#简体中文)

## English

### Principles

Coco Framework is governed as a reusable Web-server foundation. Decisions must
preserve clear module ownership, one-starter adoption, replaceable integration
points, readable generated business code, and developer ownership of domain
logic.

### Roles

- **Maintainer**: `@patton174` owns releases, repository settings, security
  response, final roadmap decisions, and CODEOWNER review.
- **Contributors**: propose changes through Issues, Discussions, and pull
  requests; they own the correctness and evidence of their contribution.
- **Coco Agent App**: opens governed same-repository pull requests, publishes
  managed review comments and bound Issues, and performs eligible merge commits.
  It never supplies the required human approval.
- **GitHub Actions**: publishes the stable CI and Agent gate contexts from
  protected workflow definitions.

### Module ownership

| Area | Ownership |
| --- | --- |
| `coco-build` | BOM, parent POM, Maven plugin, release-time build behavior |
| `coco-foundation` | Stable contracts and framework-neutral infrastructure |
| `coco-spring` | Spring Boot integration and starter composition |
| `coco-features` | Independently governed Web-server capabilities |
| `coco-support` | Repository documents, development tools, and test support |
| `coco-admin` / `coco-generate` | Separate downstream product and generator repositories |

### Decision process

Small compatible fixes may proceed directly through a focused pull request.
Breaking APIs, Maven coordinates, security defaults, module ownership, feature
contracts, or governance changes require an Issue or design discussion before
implementation. The maintainer resolves disagreements using project boundaries,
compatibility evidence, operational risk, and long-term maintenance cost.

### Protected merge and release controls

- `main` protection applies to administrators and requires strict, App-bound
  `CI gate`, `Agent jury gate`, and `Agent issue gate` checks.
- One current CODEOWNER approval and resolved conversations are required.
- Only merge commits are enabled. Force pushes and branch deletion are blocked.
- GitHub Actions are limited to GitHub-owned actions pinned to commit SHAs; the
  default token is read-only and external-fork runs require approval.
- GitHub native auto-merge is disabled. The dedicated Coco App workflow is the
  only automated merge path and revalidates the exact pull request head.
- `coco-agent` and `coco-spring` environments disallow administrator bypass and
  accept only the exact `main` branch. `coco-spring` also requires maintainer
  deployment approval before release secrets become available.
- Releases are manually dispatched from the latest protected `main` commit;
  the workflow waits for Maven Central state `PUBLISHED` and then tags the exact
  validated dispatch SHA.
- Release tags matching `v*` cannot be updated or deleted after creation.

Emergency protection changes are allowed only to repair a confirmed defect in
the protected governance runtime. The incident must be recorded publicly, and
only the single broken required context may be removed after the remaining
gates, current approval, exact-head protocol tests, independent review, and
conversation resolution succeed. The exact reviewed PR head still merges as a
merge commit; the context is restored immediately and followed by
same-repository and no-secret canaries. Other protections and direct `main`
pushes remain forbidden.

## 简体中文

### 治理原则与角色

Coco Framework 按可复用 Web 服务器基础框架治理，所有决策都应保持模块边界、单
starter 接入、可替换扩展点、可读的生成代码，以及业务团队对领域逻辑的所有权。

`@patton174` 作为维护者负责版本发布、仓库设置、安全响应、路线决策和 CODEOWNER
审批；贡献者通过 Issue、Discussion 和 PR 提案，并对改动正确性与验证证据负责。
Coco Agent App 负责受治理的 PR、评审评论、绑定 Issue 和符合条件的 merge commit，
但不能代替人工审批。

模块所有权和受保护合并规则以上方英文表格为准。涉及破坏性 API、Maven 坐标、
安全默认值、模块归属、feature 契约和治理流程的变更，必须先讨论再实现。任何紧急
保护设置调整都必须最小化、留痕、独立复核、立即恢复，并通过同仓库与无密钥
canary 验证。
