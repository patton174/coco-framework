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
| `docs/wiki` | Source of the public GitHub Wiki; a maintainer mirrors it, and it changes through pull requests like code |
| `CHANGELOG.md` | Release history in Keep a Changelog format; the maintainer finalizes version headings at release time |

### Decision process

Small compatible fixes may proceed directly through a focused pull request.
Breaking APIs, Maven coordinates, security defaults, module ownership, feature
contracts, or governance changes require an Issue or design discussion before
implementation. The maintainer resolves disagreements using project boundaries,
compatibility evidence, operational risk, and long-term maintenance cost.

### Documentation and Wiki governance

- `docs/wiki` is the source of truth for the public GitHub Wiki. It changes only
  through pull requests on the same protected merge path as code, including
  CODEOWNER review.
- Publishing to the GitHub Wiki is a manual maintainer action using
  `.github/scripts/wiki_sync.py`; automation does not push to the Wiki. Edits
  made directly in the Wiki have no review trail and no authority, and the next
  sync overwrites them.
- Wiki pages and repository documents are public. They must not contain
  credentials, personal data, or machine-specific paths.
- `CHANGELOG.md` is updated in the pull request that makes a user-visible change.
  The maintainer finalizes version headings at release time; release tags are
  still created only by the Release workflow.

### Protected merge and release controls

- Contributions enter through `dev`. A `dev-<login>` branch opens a pull request
  against `dev`, which requires strict, App-bound `CI gate`, `Agent jury gate`,
  `Agent issue gate`, and `Contributor gate` checks. The contributor gate
  verifies branch naming, that the branch suffix is the author's own login, and
  that the author is a write-access collaborator or an allow-listed project bot.
- `main` is the release branch. Its protection applies to administrators and
  requires strict, App-bound `CI gate` and `Promotion gate` checks. Only the
  repository owner may promote, only from `dev`, and only when `dev` is not
  behind `main` and carries no open bound agent-review finding. The promotion
  gate checks authority and batch integrity, not content: each change was already
  reviewed by the jury on the contributor pull request that introduced it, and
  `CI gate` re-verifies the promotion itself.
- `main` remains the GitHub default branch because `workflow_run` loads its
  **workflow definition** only from the default branch. Keeping `main` default
  therefore means a change to the reviewer's own workflow definition can only
  take effect through an owner-performed promotion, not through a routine merge
  into `dev`. The reviewer's **scripts** are checked out from the base of the
  pull request under review, so a `dev` pull request is judged by the reviewer
  code on `dev`, not by a stale copy from `main`.
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
  with the default `autoPublish=false`, the maintainer publishes the validated
  deployment in the Central Portal while the workflow polls Maven Central, and
  the workflow tags the exact validated dispatch SHA once the version is
  publicly available.
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

### 分支与发布流程（摘要）

- 贡献从 `dev` 进入：从最新 `dev` 创建 `dev-<你的 GitHub 用户名>` 分支并向 `dev` 提 PR。`dev` 要求
  `CI gate`、`Agent jury gate`、`Agent issue gate`、`Contributor gate` 四个检查，加上一次当前
  CODEOWNER 审批并解决全部评审会话。
- 维护者与 Agent 的改动使用同仓库 agent 前缀分支（默认 `codex/*`，由仓库变量
  `COCO_AGENT_BRANCH_PREFIXES` 决定），经受保护的 `Open Agent Pull Request` 工作流向 `dev` 开 PR。
- `main` 是发布分支，只能由仓库所有者把 `dev` 晋升进去，要求 `CI gate` 与 `Promotion gate`。
- 发版由维护者从最新的受保护 `main` 提交手动触发 `Release` 工作流；默认 `autoPublish=false` 时，
  维护者在 Central Portal 发布已校验的部署，工作流轮询 Maven Central 直到该版本可公开访问，
  再由 Release App 创建不可变的 `v*` 标签，不要手工推送标签。
- 仓库只启用 merge commit，禁用 squash 与 rebase。

### 文档与 Wiki 治理

- `docs/wiki` 是公开 GitHub Wiki 的事实源，只能通过 PR 修改，走与代码相同的受保护合并路径，
  包括 CODEOWNER 审批。
- 发布到 GitHub Wiki 由维护者使用 `.github/scripts/wiki_sync.py` 手动完成，自动化不会推送 Wiki。
  直接在 Wiki 网页上做的修改没有评审记录也没有效力，下一次同步会覆盖。
- Wiki 与仓库文档都是公开的，不得包含凭据、个人信息或本机路径。
- 用户可见的变更应在同一个 PR 中写入 `CHANGELOG.md`；版本标题由维护者在发版时定稿，版本标签仍只由
  Release 工作流创建。
