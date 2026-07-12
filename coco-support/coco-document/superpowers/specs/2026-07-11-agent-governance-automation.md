# Coco Agent 治理自动化规格

## 目标

本规格把 README 维护、Agent 评审发现和自动合并收敛为一条可审计的仓库治理链路：

- README 的人工可读内容按语言和主题拆分，根 README 由确定性渲染器生成。
- 架构与文字说明由低频或手动触发的 Agent 编排维护；星标、贡献者等确定性数据只由脚本维护。
- 受信 Agent 使用独立 GitHub App 身份发布评论和 PR 绑定 Issue。
- 任一开放的 PR 绑定 Agent Issue 都使 `Agent issue gate` 失败。
- 自动合并器只在精确 head 的全部机器门禁、人工批准、对话和 Agent Issue 都通过后执行 merge commit。

## 非目标

- 不在每次 push 后调用模型或改写 README。
- 不允许模型直接写根 README、workflow、评审器或合并器。
- 不让自动合并器使用 `--admin` 绕过缺失的检查、批准或未解决对话。
- 不把 fork、未固定身份 bot 或 PR head 代码放入拥有仓库密钥的执行路径。
- 不把所有仓库历史 Issue 都绑定到某个 PR；门禁只处理带受保护 marker 的 Agent Issue。

## 专用身份

仓库安装一个仅服务于 Coco 的 GitHub App。仅允许精确 `main` 分支进入的 `coco-agent` environment 通过以下配置创建短期 installation token：

- variable `COCO_AGENT_APP_CLIENT_ID`
- variables `COCO_AGENT_APP_SLUG`、`COCO_AGENT_APP_LOGIN`、`COCO_AGENT_APP_BOT_ID`
- environment secret `COCO_AGENT_APP_PRIVATE_KEY`

App 最小权限为：

| 权限 | 级别 | 用途 |
| --- | --- | --- |
| Contents | Read/Write | README 自动维护分支和最终 merge commit |
| Issues | Read/Write | Agent Issue 和受管评论 |
| Pull requests | Read/Write | 创建或更新 README PR |
| Metadata | Read | GitHub App 固有权限 |

不授予 Administration 权限。`Agent jury gate` 和 `Agent issue gate` 始终使用内置
`github.token` 发布，使同一个 required context 只有 GitHub Actions App 一个 provider；专用 App token
不得发布 gate。自动合并的可信度来自精确条件复核和分支保护，而不是管理员绕过。
私钥只进入受保护 `main` 版本的计划任务、publisher 或合并任务，不进入 specialist、verifier、
chair、fork/非受信 bot 的 no-secret reviewer，也不进入任何 PR-head 代码。同仓库且 GitHub API
返回 `User` 类型、非空 login、正整数 user ID 的普通用户，以及 login、`Bot` 类型、不可变正整数
Bot ID 与受保护仓库/environment variables 精确匹配的 Coco Agent App，可以直接进入完整评审团。Dependabot 身份固定为受保护 base Agent config 中的
`dependabot[bot]` / `Bot` / `49699333`，但 GitHub 对其原始 `pull_request_target` run 只提供
只读 token 且不提供 Actions secrets，因此该 run 必须标记 deferred + ignored，不运行模型、
不读取 secrets、不查询 approval、不发布最终 jury gate。只有受保护默认分支的 `workflow_run`
在通过 GitHub API 精确重绑定 source workflow name/path/event/run repository、source head
repository、head branch/SHA、唯一 PR、base `main`、当前 head 和作者身份后，才能调用共享完整评审团；publisher 发布前再次重绑定。延迟入口
不 checkout PR head/merge ref，也不消费 source-run artifact/cache。其他 App、名称相似但 ID
不同的 bot 和 fork 不得进入密钥路径。完整机器人评审不替代合并所需的当前 head 人类维护者批准。
同仓库的非固定身份 source run 由 binder 输出 `eligible=false` 后干净跳过，不应作为安全失败；
该临时路由文件不上传，外部 fork 在 workflow 条件中按不可变 repository identity 直接跳过。

维护者自己的改动必须推送到同仓库 `codex/*` 分支，并从受保护的最新 `main` 手动运行
`Open Agent Pull Request` workflow。该 workflow 绑定精确 branch SHA，并由专用 App 创建或
复用 PR，使 App 成为作者、维护者可以提供 required current-head approval。维护者不得直接
创建自己无法审批的 PR，也不得直接推送 `main`。

## README 源模型

### 可维护源

中英文 README 各自有一个 manifest 和多个主题片段。中文片段是结构与视觉基准，英文片段保持
同样的主题顺序和 HTML 结构。至少拆分以下边界：

- 品牌头部和语言切换
- 项目定位与引入方式
- CRUD 代码生成
- SQL、防重放和日志等生产能力
- 能力范围和框架/业务边界
- 扩展点和示例
- 架构/运行形态
- 星标历史和贡献者
- 许可证

根 `README.md` 和 `README_CN.md` 是生成物。确定性渲染器必须验证：

- manifest 只能引用仓库内允许目录的普通文件；
- 两种语言的 section ID 集合和顺序一致；
- 动态 marker 唯一且配对；
- 渲染结果以单个换行结尾；
- `--check` 模式发现 drift 时失败且不写文件。

### Agent 编排

README 维护 workflow 仅支持低频 schedule 和 `workflow_dispatch`。它从受保护 `main` 运行，读取
受保护状态文件记录的上次成功扫描 SHA，先确定性比较后续变更路径。只有命中架构、模块、示例或
说明维护范围时才调用模型；没有相关变化时不调用模型。成功扫描后更新 baseline，避免下次计划任务
重复评审同一批提交。手动内容维护可以显式强制一次新扫描。

模型输出必须是严格 JSON，只能修改 manifest 已登记的文字片段。根 README、动态片段、workflow、
脚本和状态文件不接受模型直接输出。更新顺序固定为：

1. 读取中文结构和受保护项目定位；
2. 更新需要变化的中文片段；
3. 同步对应英文片段；
4. 运行动态统计脚本；
5. 确定性渲染并执行 drift/链接/marker 校验；
6. 使用 GitHub App 创建或更新自动化 PR。

星标、派生、贡献者和更新时间由脚本从 GitHub API 获取。统计刷新可以和文字维护同一次编排，
但不能因此触发模型调用。无实际 diff 时不创建 commit 或 PR。

## Agent Issue 契约

### Marker

每个 Agent Issue 必须带 `agent-review` label，并把唯一的一行 JSON marker 放在正文首行：

```html
<!-- coco-agent-review: {"schema_version":1,"pull_request":123,"head_sha":"0123456789012345678901234567890123456789","finding_id":"v1-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"} -->
```

解析器必须严格验证字段集合、JSON 整数版本、PR 正整数、40 位小写十六进制 SHA 和受限 finding ID。
finding ID 必须是 `v1-` 加 64 位小写十六进制。普通用户文本中的相似内容、非首行 marker、非法 JSON、
额外字段、重复 marker 或错误 label 均不能参与门禁。

### 生命周期

受信 publisher 在重新验证所有模型产物和当前 PR 绑定后执行：

1. 从确定性确认的 blocker 和主席接受的有来源 follow-up 中构建可执行 finding 集合。
2. 根据规范化角色、路径、行区间、标题和 claim 计算稳定 fingerprint。
3. 对同一 PR/fingerprint 的开放 Issue 更新标题、正文、当前 head 和证据；不存在时创建。
4. 对上一轮存在、当前重评已经消失的 Issue 添加解决说明并关闭。
5. 再次扫描全部开放绑定 Issue，并使用 `github.token` 向当前 head 写 `Agent issue gate`。

Issue 正文必须链接来源 PR、首次发现 head、当前验证 head、finding 来源、严重度、代码位置、触发、
影响、证据和验证方式。Issue 不能取代 PR 汇总评论；评论仍展示完整评审团结果和 Issue 链接。

任何 Issue API、身份、binding 或 gate 发布失败都失败关闭。fork/未固定身份 bot 的 no-secret reviewer 不注入 App 私钥、
不创建 Issue、不写评论；它仍需要当前 head 的维护者批准，并由独立 issue-gate workflow 扫描已有绑定 Issue。

### Gate 重算

`Agent issue gate` 使用受保护 base/default-branch 脚本，至少在以下事件后重算：

- PR opened、reopened、synchronize、ready-for-review
- Agent publisher 完成同步
- 绑定 Issue closed 或 reopened
- 手动 dispatch

Issue 事件必须从严格 marker 解析 PR，再读取 GitHub 当前 head；不能相信 Issue 标题或自由文本中的 PR 号。
状态写入前后都要确认 PR 仍 open、base 为 `main` 且 head 未变化。

## 自动合并

自动合并 workflow 只执行受保护默认分支中的脚本，并使用 GitHub App installation token。触发可以来自
CI/Agent workflow 完成、绑定 Issue 关闭/重开、受保护 `main` 上的定时扫描和手动 dispatch，但事件只提供候选 PR 号，
不能直接授权合并。审批和 review thread 变化由十分钟定时扫描发现；`pull_request_review` 使用未受保护的
PR merge ref，不能进入持有 App 私钥的 `coco-agent` environment。

对候选 PR 必须重新查询并同时满足：

- state 为 open、base 为 `main`、非 draft；
- head SHA 是 40 位小写十六进制且在整个检查过程中不变；
- GitHub 报告 `mergeable=true`，且分支已与 `main` 同步；REST `mergeable_state` 只接受
  `clean` 或 `unstable`，并拒绝 `behind`、`blocked`、`dirty`、`draft` 和 `unknown`；
- 当前 head 至少有一个有效的非 bot 维护者 `APPROVED` review；
- `CI gate`、`Agent jury gate`、`Agent issue gate` 对当前 head 均为 success，且发布 provider
  分别是受保护配置允许的 GitHub Actions App/check actor；
- 没有未解决 review thread；
- 没有任何开放的严格绑定 Agent Issue；
- 仓库仍只允许 merge commit。

`unstable` 只表示 GitHub 仍观察到非必需状态的 pending/failure，例如用于串行化发布器的
`Agent jury ownership` 诊断状态；它不能直接授权合并。自动合并器仍须独立验证由 GitHub Actions
App `15368` 发布的三个 required gate，并完成审批、会话、Agent Issue、仓库合并设置和第二次
精确 head 全量复核。任何 required gate 不是 success 时，即使 REST 返回 `unstable` 也必须阻断。

执行 merge API 时必须携带期望 head SHA 和 `merge` 方法。调用前必须完整重跑 PR、批准、三个 gate、
review threads、开放 Issue 和仓库合并设置，而不只是二次读取 head；SHA、状态或任一条件变化则退出，
不合并。脚本支持 dry-run，并对 API 分页、重复事件和并发执行保持幂等。

## 自举顺序

本变更同时修改 `pull_request_target` reviewer、共享 reusable jury 和默认分支 `workflow_run`
密钥入口，不能由 PR head 自托管。发布顺序固定：

1. 使用当前受保护 base reviewer 完成 PR 的 `CI gate`、`Agent jury gate`、人工批准和对话解决。
2. 合并后创建并安装 GitHub App，或在合并前完成配置但不让 PR head 使用私钥。
3. 从新 `main` 运行协议测试和 README dry-run。
4. 创建 same-repository canary，验证 App 评论、Issue 创建/关闭和三个 gate 的精确 SHA 绑定。
5. 创建固定身份 Dependabot 等价 canary，验证原始 run 无 secret/无最终 gate、延迟 run 精确
   重绑定并完成完整评审团和 App publisher；再创建未固定 bot/fork 等价 canary，验证没有
   Anthropic/App 私钥、模型 job 跳过且已有 Issue 仍能阻断。
6. 创建自动合并 canary，确认缺批准/检查/Issue/对话时不合并，全部满足后由 App 生成 merge commit。
7. Canary 全部通过后，把 `Agent issue gate` 加入 `main` required checks。

受保护 base reviewer 自身故障时，紧急自举也不得执行 PR-head 密钥代码或关闭整套保护。
必须先创建公开 Issue，确认失败来自治理运行时而不是有效 P0/P1 finding，并让 `CI gate`、
精确 head 协议测试、App-authored PR 的当前人工批准、独立复核和全部会话解决。之后只临时
移除故障的单个 required context，通过 PR merge commit 合并精确已评审 head，立即恢复原
App ID 绑定 context，并完成同仓库与无密钥 canary。其他 required checks、审批、管理员保护、
禁止 force push/删除分支等设置保持不变，任何情况下都不得直接 push `main`。

## 验收

- README renderer、Agent 输出约束、动态 marker、Issue marker、Issue 对账、gate 和 auto-merge 条件都有离线测试。
- Python `unittest`、`py_compile`、Ruff、actionlint、ShellCheck 和 `git diff --check` 通过。
- README `--check` 对当前根文档通过；重复渲染不产生 diff。
- App 创建的评论、Issue、分支、PR 和 merge commit 在 GitHub UI 中显示专用 bot 身份。
- 任一开放绑定 Issue 都让 required `Agent issue gate` 阻断合并；关闭后只对仍然相同的当前 head 恢复。
- 自动合并日志输出每个条件的机器可读结果，但不输出 token、私钥或模型内容。
- 源码或脚本修改后执行 `codegraph sync .`。
