# Coco Agent 治理自动化规格

<!-- coco-agent-deferred-binding-contract:v1 {"canonical":["ID","name","path","state"],"source":["workflow_id","path","event","repository"],"association":["structured pull_requests","current PR re-fetch"],"jobs":{"route":"success","marker":"success","others":"skipped"},"untrusted":["run-name","name","display_title"]} -->

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
| Administration | Read | Auto Merge 读取受保护 `main` 的 required status checks |
| Metadata | Read | GitHub App 固有权限 |

禁止授予 `Administration: write`；安装级 `Administration: read` 只能由 Auto Merge 的独立
protection token 请求和使用，不得用于修改 branch protection。`Agent jury gate` 和 `Agent issue gate` 始终使用内置
`github.token` 发布，使同一个 required context 只有 GitHub Actions App 一个 provider；专用 App token
不得发布 gate。自动合并的可信度来自精确条件复核和分支保护，而不是管理员绕过。
App 私钥只进入受保护 `main` 的 `workflow_run` publisher、计划或合并任务，不进入 source 或
PR-head 代码。`pull_request_target` 调用 reusable 时 environment ref 绑定 PR head，因此 source
不得调用 secret-backed job；环境不得放宽到 `codex/*`，密钥不得迁移到 repository secrets。

同仓有效 `User`、精确 App、固定 Dependabot 先 no-secret marker；eligible run
仅 `deferred + ignored`。仅 `refs/heads/main` `workflow_run` 在 API 解析
`.github/workflows/agent-review.yml` canonical workflow API identity（`ID`/`name`/`path`/`state`）后，
绑定 source `workflow_id`/path/event/repository、唯一成功 router/marker jobs（其他 jobs 必须 skipped）、唯一结构化
`pull_requests` PR/base/head、current PR re-fetch、branch/exact author，才以 `allow_deferred=true` 调用
评审团；publisher 重绑。`run-name`、evaluated `name`、`display_title` 是 PR-context，绝不作为
identity 或 PR binding 的可信输入。延迟入口不 checkout
PR head/merge ref，不消费 source artifact/cache。其他 bot/fork 与非固定同仓身份无密钥路径，返回
`eligible=false`；完整评审不替代当前 head 人类维护者批准。

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
finding ID 必须是 `v1-` 或 `v2-` 加 64 位小写十六进制；新 actionable group 写入 `v2-`，
历史受管 Issue 可以保留其 `v1-` marker 直到受信 publisher 明确对账。普通用户文本中的相似内容、非首行 marker、非法 JSON、
额外字段、重复 marker 或错误 label 均不能参与门禁。

### 生命周期

受信 publisher 在重新验证所有模型产物和当前 PR 绑定后执行：

1. 从确定性确认的 blocker 和主席接受的 `actionable_groups` 中构建可执行集合。
2. group 只能包含同一确定性 finding identity 的同 kind、同严重级别成员；所有 confirmed
   blocker 必须恰好属于一个 group。
3. 对同一 PR/group identity 的开放 Issue 更新标题、正文、当前 head 和证据；不存在时创建。
   当前 `v2-` group 也可以精确匹配其成员携带的一个历史 `v1-` alias。该匹配仅限同一 PR、
   仅接受唯一候选；采用后把 marker 更新为 `v2-`，保留首次 head 和历史审计关系。多个候选、
   一个 Issue 被多个 group 认领、或 alias 非法时在任何 Issue 写入前失败关闭。其他 PR marker
   下的同 ID/alias 无论 Issue 开放或关闭均不参与当前 PR 的 identity 或歧义判断。
4. 对上一轮存在、当前重评已经消失的 Issue 添加解决说明并关闭。
5. 在任何 route binding failure status、gate status、label、Issue、comment、close 或 reopen
   写入前预检 `max_actionable_issue_groups`；超限时保持零仓库写副作用并直接失败退出。
6. 再次扫描全部开放绑定 Issue，并使用 `github.token` 向当前 head 写 `Agent issue gate`。

不得凭标题/正文/模型文字/语义相似性认领 Issue，或用 previous head 恢复 identity/重试操作；
cross-head identity continuity 不在本协议。

受信 Issue/comment 写入使用严格 canonical operation marker，绑定 repository/ID、App login/Bot ID、run/attempt、
PR/head/group 与枚举 action；Issue finding marker 仍为首行，operation marker 为次行。2xx 空/null/坏 JSON/结构响应
不重发；有界读取经 PR 前后复核。写前快照仅为 pending；actor/identity/target ID/body/state 全匹配才 exact。新 run、
错 identity、重复/冲突/head 漂移立即 fail closed，0 匹配耗尽失败。衍生写入须在首写前校验正文预算。
status 须用正整数 ID 和 client API URL 绑定 endpoint，且匹配 state/context/description/target URL；status/label
不恢复/重发。日志仅含 action/attempt/path。

Issue 正文必须链接来源 PR、首次发现 head、当前验证 head、finding 来源、严重度、代码位置、触发、
影响、证据和验证方式。Issue 不能取代 PR 汇总评论；评论仍展示完整评审团结果和 Issue 链接。

任何 Issue API、身份、binding 或 gate 发布失败都失败关闭。verifier 或 chair 任一缺失、其
`head_sha`、`context_sha256`、输入 digest 或受保护角色不匹配时，publisher 不得发布成功
结果，也不得以部分报告替代完整 jury。模型截断恢复只能由受保护 base runtime 在固定三次
调用内执行，且最终必须重新通过完整 JSON、身份和 binding 校验；日志仅可记录受控停止原因、
尝试次数、长度和 hash 前缀，不得记录原始模型片段、提示词或 secrets。fork/未固定身份 bot 的 no-secret reviewer 不注入 App 私钥、
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

自动合并使用三个相互分离的客户端能力：普通 `github.token` 只读取 PR、review、check、status 和 Issue；
专用 Coco App 单独铸造只含 `Administration: read` 的短期 token，且客户端只暴露 `main` branch protection
required-status-checks 读取；另一个 App token 只含 `Contents: write` 并只执行带 exact head 的 merge API。
普通读取 token 和 merge token 均不得读取 branch protection，protection token 不得写 Contents。Auto Merge
不拥有或调用 branch protection 管理写权限。App token 创建、权限、API 或解析失败全部 fail closed。

对候选 PR 必须重新查询并同时满足：

- state 为 open、base 为 `main`、非 draft；
- head SHA 是 40 位小写十六进制且在整个检查过程中不变；
- GitHub 报告 mergeable，且分支已与 `main` 同步；
- 当前 head 至少有一个有效的非 bot 维护者 `APPROVED` review；
- 自动合并器从 `main` 当前 branch protection 的 `required_status_checks` 读取 required check
  集合，而不自行用静态常量覆盖保护策略。响应必须为 strict，legacy `contexts` 与 App-bound
  `checks` 必须同时存在、集合完全一致且 context 唯一；每项必须绑定 GitHub Actions App ID
  `15368`。唯一允许的集合为标准的 `CI gate`、`Agent jury gate`、`Agent issue gate`，或已记录
  受保护 base 事故期间仅暂时缺少 `Agent jury gate` 的 `CI gate`、`Agent issue gate`。缺少 CI
  或 Issue、未知 context、重复 context、缺失或错误 App ID、畸形响应或 API 失败均拒绝合并；
- 标准三 gate 路径不接受 incident Issue 参数。仅缺 `Agent jury gate` 的两 gate 路径必须由
  仓库 owner 本人触发 `workflow_dispatch` 并显式提供一个公开 incident Issue number；初始 `actor` 和重跑时的
  `triggering_actor` 都必须精确等于仓库 owner，普通自动触发、协作者或维护者 dispatch/re-run 均不能进入该路径。
  候选 PR 必须由配置中钉住 login 和不可变 Bot ID 的专用 Coco Framework
  Agent App 创建；Dependabot、用户或其他 App 创建的 PR 均拒绝。仓库 owner 还必须对 exact current head
  提交当前有效的 `APPROVED` review，普通维护者批准或 head 更新前的 owner 批准不能替代；
- incident Issue 仅记录事故审计和 exact binding，不作为授权者身份凭据。Issue 必须不是 PR、仍 open、由仓库
  owner 的 `User` 身份创建，并且正文中恰有一个
  canonical `coco-auto-merge-incident` JSON marker。marker schema 精确包含 `schema_version`、`repository`、
  `base_sha`、`pull_request`、`head_sha`、`missing_context`、`issued_at` 和 `expires_at`；只能绑定当前仓库、
  当前受保护 base SHA、一个 exact PR/head，并且 `missing_context` 只能为 `Agent jury gate`。授权时间必须使用
  RFC3339 UTC 表示；接受 `Z`、`+00:00` 和包含一位或多位数字的 secfrac。Python `datetime` 微秒精度之外的
  secfrac 仅在超出六位的数字全为 `0`、因而可无损规范化时接受；任何非零亚微秒部分均 fail closed，不得通过
  舍入或有损截断放宽授权窗口。非 UTC、naive 或畸形值也必须拒绝。当前时间必须位于 issued/expires 区间内，
  且总有效期不得超过 24 小时。Issue、作者、marker、时间或
  API 任一异常均 fail closed；标题、可编辑正文自身、自然语言、评论、label 和通配字段都不构成身份授权。
  即使协作者向 owner 创建的 Issue 注入合法 marker，没有 owner dispatch、指定 App PR 作者和 owner exact-head
  approval 也必须拒绝；
- 从上述保护配置派生的每个 gate 对当前 head 均为 success，且同名 status/check 仍必须来自受信
  GitHub Actions provider，不能以动态配置接受伪造的同名信号；
- 没有未解决 review thread；
- 没有任何开放的严格绑定 Agent Issue；
- 仓库仍只允许 merge commit。

执行 merge API 时必须携带期望 head SHA 和 `merge` 方法。调用前必须完整重跑 PR、批准、从保护配置
派生的 gate、review threads、开放 Issue、仓库合并设置和 branch protection；而不只是二次读取 head。
两次 eligibility 读取的 required gate 集合或任一 App binding 不同，或 SHA、状态或任一条件变化，均退出
且不合并。两 gate 路径还必须在两次读取中重新验证 owner dispatch actor、指定 App PR 作者、owner
exact-head approval，并重新获取同一个 incident Issue 和完全相同的 exact binding；actor、PR 作者、owner
approval、head、Issue、marker、作者、时间或保护配置任一变化都立即停止。PR 合并后不再是 open candidate，且 marker 仅绑定
该 PR/head/base，因此同一授权不能用于其他 PR。脚本支持 dry-run，并对 API 分页、重复事件和并发执行保持幂等。

## 自举顺序

本变更同时修改 `pull_request_target` reviewer、共享 reusable jury 和默认分支 `workflow_run`
密钥入口，不能由 PR head 自托管。发布顺序固定：

1. 使用当前受保护 base reviewer 完成 PR 的 `CI gate`、`Agent jury gate`、人工批准和对话解决。
2. 合并后创建并安装 GitHub App，或在合并前完成配置但不让 PR head 使用私钥。
3. 从新 `main` 运行协议测试和 README dry-run。
4. 创建 same-repository human 与精确 Coco App canary，验证 source run 只有 router/marker 成功、
   `workflow_run` 和两个 secret environment 的 deployment ref 为 `main`，并验证 App 评论、
   Issue 创建/关闭和三个 gate 的精确 SHA 绑定。
5. 创建固定身份 Dependabot 等价 canary，验证原始 run 无 secret/无最终 gate、延迟 run 精确
   重绑定并完成完整评审团和 App publisher；再创建未固定 bot/fork 等价 canary，验证没有
   模型 API key/App 私钥、模型 job 跳过且已有 Issue 仍能阻断。
6. 创建自动合并 canary，确认缺批准/检查/Issue/对话时不合并，全部满足后由 App 生成 merge commit。
7. Canary 全部通过后，把 `Agent issue gate` 加入 `main` required checks。

只有 specialist/verifier/chair 声明 `coco-agent-model` 并读取 API key；prepare/admission 仅读取
三项受保护 repository variables 以绑定摘要和检测漂移。reusable/callers 不传递或继承 secrets，README
旧配置在单独迁移前也不得被 Agent Review 使用。

受保护 base reviewer 自身故障时，紧急自举也不得执行 PR-head 密钥代码或关闭整套保护。
必须先创建公开 Issue，确认失败来自治理运行时而不是有效 P0/P1 finding，并让 `CI gate`、
精确 head 协议测试、App-authored PR 的当前人工批准、独立复核和全部会话解决。之后只临时
移除故障的单个 required context，通过 PR merge commit 合并精确已评审 head，立即恢复原
App ID 绑定 context，并完成同仓库与无密钥 canary。其他 required checks、审批、管理员保护、
禁止 force push/删除分支等设置保持不变，任何情况下都不得直接 push `main`。
执行该路线前必须在 dedicated Coco App 安装中显式授予 `Administration: read`，合并本修复后先对同仓
exact PR/head/incident 连续运行两次 protection-read dry-run canary，证明独立 token 能完成两次读取且不含
Contents 写权限。事故 PR 合并后管理员必须立即恢复 App ID `15368` 绑定的 `Agent jury gate`，再运行
same-repository 和 no-secret canary；Auto Merge 不修改保护配置，也不能代替该恢复操作。

## 验收

- README renderer、Agent 输出约束、动态 marker、Issue marker、Issue 对账、gate 和 auto-merge 条件都有离线测试。
- Python `unittest`、`py_compile`、Ruff、actionlint、ShellCheck 和 `git diff --check` 通过。
- 协议测试证明 source workflow 不声明 environment/secret-backed caller，workflow_run 只在
  `refs/heads/main` 放行，并拒绝错误 PR/base/head、仓库或 App login/type/ID、缺失/失败/重复
  marker 和任何非 skipped 的额外 source job。
- README `--check` 对当前根文档通过；重复渲染不产生 diff。
- App 创建的评论、Issue、分支、PR 和 merge commit 在 GitHub UI 中显示专用 bot 身份。
- 任一开放绑定 Issue 都让 required `Agent issue gate` 阻断合并；关闭后只对仍然相同的当前 head 恢复。
- 自动合并日志输出每个条件的机器可读结果，但不输出 token、私钥或模型内容。
- 源码或脚本修改后执行 `codegraph sync .`。
