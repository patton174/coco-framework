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

1. Create a focused branch named `dev-<your-github-login>` from current `dev`,
   and open the pull request against `dev`. `main` is the release branch: only
   the repository owner promotes `dev` into it, so a pull request targeting
   `main` from any other branch is rejected by the `Promotion gate`.
2. Keep the change scoped to one module, contract, or governance concern.
3. Use JDK 21; published bytecode targets Java 17.
4. Preserve public APIs unless the pull request explicitly declares and
   justifies a breaking change.
5. Never commit credentials, production data, private keys, generated build
   output, or local IDE state.
6. Write commit subjects in English using Conventional Commits (`feat:`,
   `fix:`, `docs:`, `refactor:`, `test:`, `ci:`, `build:`, `chore:`; add `!`
   for a breaking change), as the existing history does. Project documents and
   JavaDoc descriptions are Chinese-first (see [AGENTS.md](AGENTS.md));
   identifiers, commands, and paths stay as written.

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

For documentation, Wiki, or changelog changes:

```powershell
python .github/scripts/test_wiki_sync.py
python .github/scripts/wiki_sync.py check
```

`README.md` and `README_CN.md` are generated. Edit paired fragments under
`.github/readme/fragments/`, then run:

```powershell
node .github/readme/scripts/render.mjs --write
node .github/readme/scripts/render.mjs --check
```

### Documentation, Wiki, and changelog

The developer Wiki is maintained as Markdown under [docs/wiki](docs/wiki) and
mirrored to the GitHub Wiki by a maintainer. Change it through a pull request
like code; never edit the Wiki in the GitHub web UI, because the next sync
overwrites the change and the web UI bypasses review. The
[documentation index](docs/README.md) links the Wiki, the changelog, and the
other documentation sets. Record user-visible and contributor-visible changes in
[CHANGELOG.md](CHANGELOG.md) under `[Unreleased]` (Keep a Changelog); the
maintainer moves them under a version heading at release time.

Documentation pull request checklist:

- [ ] Wiki pages stay flat with ASCII `Title-Case-With-Hyphens.md` names, and
      every new or renamed page is listed in `_Sidebar.md` and `Home.md`.
- [ ] `python .github/scripts/wiki_sync.py check` and
      `python .github/scripts/test_wiki_sync.py` pass.
- [ ] Repository files are linked with `https://github.com/patton174/coco-framework/blob/main/<path>`
      URLs that exist; wiki pages are linked as `[text](Page-Name)`.
- [ ] No local machine paths, e-mail addresses, or credentials; secrets are
      referenced by key name only.
- [ ] `CHANGELOG.md` has an `[Unreleased]` entry, or the pull request says why no
      entry is needed.
- [ ] `README.md` and `README_CN.md` changed only through
      `.github/readme/fragments/` and `render.mjs --write`.

### Pull requests

Complete the pull request template with the intent, affected modules,
compatibility impact, and exact verification performed. Link the governing
Issue when one exists. Review generated code before committing it.

Maintainer-authored changes must be pushed to a same-repository agent branch
(`codex/*` by default; the allowed prefixes are the repository variable
`COCO_AGENT_BRANCH_PREFIXES`, for example `codex/,claude/`) and submitted through
the protected `Open Agent Pull Request` workflow
from current `main`. The workflow opens the pull request against `dev`. The
Coco App becomes the pull request author so the human
maintainer can provide the required current-head CODEOWNER approval. Do not open
an owner-authored pull request directly, because GitHub does not allow its author
to approve it.

Branch names must be `dev-<your-github-login>` with your own login as the
suffix; `dev-someone-else` is rejected. Fork pull requests are not accepted, and
the author must be a repository collaborator with write access or an
allow-listed project bot.
`dependabot/*` and the configured agent prefixes are exempt from that naming,
each usable only by its own bot. The agent prefix is a label, not an identity:
only the Coco App may use it, so a human cannot borrow it. A prefix takes effect
for the Contributor gate once it is on `dev`, and for the `Open Agent Pull
Request` workflow once that change has been promoted to `main`.

A pull request into `dev` must pass `CI gate`, `Agent jury gate`,
`Agent issue gate`, and `Contributor gate`, plus one current CODEOWNER approval
with conversations resolved. See GOVERNANCE.md for the `main` promotion rules.
The repository uses merge commits; squash and rebase are disabled.

Releases are not part of the contributor flow. The maintainer dispatches the
`Release` workflow manually from the latest protected `main` commit; see
[GOVERNANCE.md](GOVERNANCE.md) and the
[release notes for maintainers](coco-support/coco-document/release.md).

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

1. 从最新 `dev` 创建名为 `dev-<你的 GitHub 用户名>` 的聚焦分支，并向 `dev` 提 PR。
   `main` 是发布分支，只有仓库所有者能把 `dev` 合并进去；其他分支直接提 `main`
   会被 `Promotion gate` 拒绝。
2. 每个 PR 只处理一个模块、契约或治理问题。
3. 使用 JDK 21 开发，发布字节码目标为 Java 17。
4. 除非 PR 明确声明并论证破坏性变更，否则不得随意改变公开 API。
5. 不得提交凭据、生产数据、私钥、构建产物或本地 IDE 状态。
6. 提交标题使用英文 Conventional Commits（`feat:`、`fix:`、`docs:`、`refactor:`、`test:`、
   `ci:`、`build:`、`chore:`，破坏性变更加 `!`），与现有提交历史一致。项目文档与 JavaDoc 描述以中文为主
   （见 [AGENTS.md](AGENTS.md)），标识符、命令和路径保持原样。

仓库存在 `.codegraph/` 时，应先使用 CodeGraph 理解代码，并在源码变更后执行
`codegraph sync .`。

验证命令与 README 生成方式见上方英文部分。PR 必须写明变更意图、影响模块、
兼容性和实际执行的验证。维护者自己的改动必须推送到同仓库 agent 分支（默认 `codex/*`，
允许的前缀由仓库变量 `COCO_AGENT_BRANCH_PREFIXES` 决定，例如 `codex/,claude/`），并从最新
`main` 运行受保护的 `Open Agent Pull Request` 工作流，该工作流会向 `dev` 开 PR，由 Coco App 作为 PR 作者，
维护者再提供当前 head 的人工审批。

分支名必须是 `dev-<你的 GitHub 用户名>`，后缀须为本人用户名；`dev-别人的名字` 会被
拒绝。不接受 fork PR，且作者必须是具有写权限的仓库协作者或在白名单中的项目 bot。`dependabot/*` 与已配置的 agent 前缀豁免该命名，且各自只有对应 bot 可用；
agent 前缀只是标签而不是身份，只有 Coco App 能使用，人类账号无法借用。新增前缀对 Contributor gate
在进入 `dev` 后生效，对 `Open Agent Pull Request` 工作流则要等该改动晋升到 `main` 后才生效。

提到 `dev` 的 PR 必须通过 `CI gate`、`Agent jury gate`、`Agent issue gate` 和
`Contributor gate`，并获得当前 CODEOWNER 审批、解决全部评审会话。`main` 的晋级规则见
GOVERNANCE.md。仓库只使用 merge commit，squash 与 rebase 已禁用。

发版不属于贡献者流程：维护者从最新的受保护 `main` 提交手动触发 `Release` 工作流，详见
[GOVERNANCE.md](GOVERNANCE.md) 与[维护者发布说明](coco-support/coco-document/release.md)。

### 文档、Wiki 与更新日志

开发者 Wiki 以 Markdown 形式维护在 [docs/wiki](docs/wiki)，由维护者镜像发布到 GitHub Wiki。修改 Wiki 要像改代码一样通过
PR；不要在 GitHub 网页上直接编辑 Wiki，网页编辑绕开评审，并会被下一次同步覆盖。[文档索引](docs/README.md)
链接了 Wiki、更新日志和其他文档。用户可见或贡献者可见的变更，请在 [CHANGELOG.md](CHANGELOG.md) 的
`[Unreleased]` 下追加条目（Keep a Changelog 格式），由维护者在发版时移到版本标题下。

文档类 PR 检查项：

- [ ] Wiki 页面保持扁平，文件名为 ASCII 的 `Title-Case-With-Hyphens.md`；新增或改名的页面已列入
      `_Sidebar.md` 与 `Home.md`。
- [ ] `python .github/scripts/wiki_sync.py check` 与 `python .github/scripts/test_wiki_sync.py` 通过。
- [ ] 仓库文件使用确实存在的 `https://github.com/patton174/coco-framework/blob/main/<路径>` 链接，
      Wiki 页面之间使用 `[文字](Page-Name)`。
- [ ] 不含本机路径、邮箱或凭据；密钥只写键名。
- [ ] `CHANGELOG.md` 已有 `[Unreleased]` 条目，或在 PR 中说明无需记录的原因。
- [ ] `README.md` 与 `README_CN.md` 只通过 `.github/readme/fragments/` 和 `render.mjs --write` 变更。
