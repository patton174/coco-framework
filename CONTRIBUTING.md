# Contributing to Coco Framework

[English](#english) | [简体中文](#简体中文)

## English

### Start with the project boundary

Before proposing a change, read [AGENTS.md](AGENTS.md),
[GOVERNANCE.md](GOVERNANCE.md), and the
[2.0 module layout](coco-support/coco-document/architecture/module-layout.md).
Coco Framework owns reusable Web-server infrastructure. Product-specific ERP,
authentication, organization, workflow, report, and business transaction logic
belong in downstream applications.

Use [Discussions](https://github.com/patton174/coco-framework/discussions) for
questions and design exploration. Open an Issue before implementing a breaking
API, Maven coordinate, feature contract, security, or repository-governance
change.

### Development workflow

1. Fork the repository or create a focused branch from current `main`.
2. Keep the change scoped to one module, contract, or governance concern.
3. Use JDK 21; published bytecode targets Java 17.
4. Preserve public APIs unless the pull request explicitly declares and
   justifies a breaking change.
5. Never commit credentials, production data, private keys, generated build
   output, or local IDE state.

When `.codegraph/` exists, use CodeGraph before broad source searches and run
`codegraph sync .` after source changes.

### Verification

Run the checks that match the affected surface. The normal full verification is:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B clean verify
```

For release-sensitive changes:

```powershell
mvn -B -Prelease '-Drevision=2.0.1' '-Dgpg.skip=true' '-DskipTests' verify
```

For governance or workflow changes:

```powershell
python .github/scripts/test_agent_review.py
python .github/scripts/test_auto_merge.py
python -m ruff check .github/scripts
python -m ruff format --check .github/scripts
node --test .github/readme/tests/readme.test.mjs
node .github/readme/scripts/render.mjs --check
```

`README.md` and `README_CN.md` are generated. Edit paired fragments under
`.github/readme/fragments/`, then run:

```powershell
node .github/readme/scripts/render.mjs --write
node .github/readme/scripts/render.mjs --check
```

### Pull requests

Complete the pull request template with the intent, affected modules,
compatibility impact, and exact verification performed. Link the governing
Issue when one exists. Review generated code before committing it.

Maintainer-authored changes must be pushed to a same-repository `codex/*`
branch and submitted through the protected `Open Agent Pull Request` workflow
from current `main`. The Coco App becomes the pull request author so the human
maintainer can provide the required current-head CODEOWNER approval. Do not open
an owner-authored pull request directly, because GitHub does not allow its author
to approve it.

Merges require the protected `CI gate`, `Agent jury gate`, and
`Agent issue gate`, one current CODEOWNER approval, and resolved review
conversations. The repository uses merge commits; squash and rebase merges are
disabled.

## 简体中文

### 先确认项目边界

提交改动前，请阅读 [AGENTS.md](AGENTS.md)、[GOVERNANCE.md](GOVERNANCE.md)
以及 [2.0 模块布局](coco-support/coco-document/architecture/module-layout.md)。
Coco Framework 只承载可复用的 Web 服务器基础设施。ERP 领域、认证模型、组织权限、
工作流、报表和业务事务应由下游业务项目负责。

一般问题和方案讨论请使用
[Discussions](https://github.com/patton174/coco-framework/discussions)。涉及公开 API、
Maven 坐标、feature 契约、安全策略或仓库治理的破坏性变更，应先创建 Issue。

### 开发流程

1. 从最新 `main` 创建聚焦分支，或从个人 fork 发起贡献。
2. 每个 PR 只处理一个模块、契约或治理问题。
3. 使用 JDK 21 开发，发布字节码目标为 Java 17。
4. 除非 PR 明确声明并论证破坏性变更，否则不得随意改变公开 API。
5. 不得提交凭据、生产数据、私钥、构建产物或本地 IDE 状态。

仓库存在 `.codegraph/` 时，应先使用 CodeGraph 理解代码，并在源码变更后执行
`codegraph sync .`。

验证命令与 README 生成方式见上方英文部分。PR 必须写明变更意图、影响模块、
兼容性和实际执行的验证。合并必须通过三个稳定门禁、当前 CODEOWNER 审批并解决
全部评审会话；仓库只使用 merge commit。维护者自己的改动必须推送到同仓库
`codex/*` 分支，并从最新 `main` 运行受保护的 `Open Agent Pull Request` 工作流，由
Coco App 作为 PR 作者，维护者再提供当前 head 的人工审批。
