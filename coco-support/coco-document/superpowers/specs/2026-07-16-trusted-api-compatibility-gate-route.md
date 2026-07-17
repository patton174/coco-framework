# 受信公共 API 兼容性 Shadow 门禁路由

## 状态

- 当前为 Phase 1 dormant 协议。唯一受信 status 为 `API compatibility trusted shadow`。
- 该 context 不是 required check，不参与自动合并，也不改变 `CI gate`、`Agent jury gate` 或 `Agent issue gate`。
- 开关只有 repository variable `COCO_API_COMPATIBILITY_TRUSTED_SHADOW`。缺失或非精确 `true` 时，自动 producer、`workflow_run` bind、verify 和 publish 都不运行。
- `workflow_dispatch` 只允许受保护分支对 policy bundle 做 dormant preflight；它不生产候选、不发布 status，也不能替代 canary。

## 信任边界

候选 producer 只从 `pull_request` 或 `merge_group` 运行。它拥有 `contents: read`，没有 secrets、environment 或 write permission；fork checkout 必须使用 event 所给的 fork repository 和 exact head SHA。

producer 只能执行候选 Maven build，并上传短期 artifact：顶层 `manifest.json` 加严格 32 个 `jars/<artifact>.jar`。manifest 是非权威的，仅用于把 archive 内的字节 hash 绑定到候选 run。producer 不 checkout protected code，不运行 checker，不生成或上传 XML、proof、verdict、report、POM 或 policy 文件。candidate checkout 在 checkout、build、stage 和 upload 前后都必须保持 exact HEAD、clean index、clean tracked 文件和没有 nonignored untracked 文件。

候选 Maven/POM、manifest、JAR 名称和 archive 内任意文本不授予语义结论。producer job 永远不是 `CI gate` 的 dependency。

## 受保护验证

`API Compatibility Trusted Shadow` 仅由默认分支的 `workflow_run` 执行，并只消费工作流名和路径均为 `CI` / `.github/workflows/ci.yml` 的 completed source run。每个 bind、verify 和 publish 都重新检查：

- event snapshot、source run ID、attempt、workflow ID/name/path、repository、source event、head SHA 和当前 protected `main` head；
- PR 必须唯一、open、base 为 `main`，且 API 的 head repository/ref/SHA 与 run 一致；fork 只按其 own repository 和 exact SHA 读取；
- merge queue 必须是 `gh-readonly-queue/main/...` synthetic ref；
- 同一 event/head 的更高 run ID 或 attempt 会拒绝旧运行；publish 也拒绝覆盖更高 owned shadow status；
- protected checkout 在 checkout、policy read、artifact verify 和 publish 前后均检查 HEAD、index、tracked 与 nonignored untracked clean。

`verify-jars` 从 exact protected SHA checkout 并重新计算 canonical JSON policy bundle：

- `public-api-profile.json` 定义排序的 32 项 inventory，其中 20 项有 baseline，12 项标记 n.a.；
- `baseline-ledger.json` 只能覆盖那 20 项，且每个 baseline 的 HTTPS URL、size 和 SHA-256 都受约束；
- `allowlist.json` 与 `japicmp-key.json` 同样必须 canonical；key 固定 japicmp `0.23.1` 的 URL、size `5988558` 和 SHA-256 `f2300a8531b68e25b678247874a1eae13a07d6842a4a1236845481fc90c5c6c7`；
- 任何 asset 缺失、symlink、非 canonical JSON、schema 偏差、inventory/ledger 偏差或 pin 偏差都 fail closed。基础 assets 合并前，路由保持 dormant，不能启用 shadow。

verifier 独立下载 pinned japicmp fat JAR，验证 size/hash，然后安全解析 artifact，不信任 producer manifest 来决定 inventory。archive 必须且只能包含 profile 推导的 32 个 JAR 和一个 manifest；缺失、额外、重复、case collision、path traversal、backslash、symlink、加密 entry、损坏 ZIP、压缩炸弹、coordinate/JAR 交换都失败。验证器将合格 JAR 与 ledger baseline 重跑 japicmp API/ABI 语义；producer XML 永远不是输入。

## 权限与发布

- `bind` 与 `verify-jars` 仅有 `actions: read`、`contents: read`、`pull-requests: read`；没有 `statuses: write`。
- `publish` 是唯一拥有 `statuses: write` 的 job。它不调用 artifact API，不下载、解压或解析 artifact，也不运行 japicmp。
- verifier 到 publisher 的唯一数据为精确 8 ASCII byte token：`PASS0000` 或 `FAIL0001` 到 `FAIL0005`。未知长度、newline 或其他值失败。
- publisher 在 POST 前重新 bind source run/attempt/head，并且只向 exact candidate SHA 发布固定 context `API compatibility trusted shadow`。它绝不发布、更新或冒充 `CI gate`。

## 验收

离线协议测试覆盖：breaking JAR 加伪 XML、policy/POM 污染、dirty tracked/untracked/index、缺失 policy、31/33 JAR、duplicate/case collision、zip bomb、stale run/attempt/head、fork、merge group 与 publisher 非法 token。每次路由变更还必须执行 Python 编译、Ruff、YAML 解析、可用 governance tests、`git diff --check` 和（若本 worktree 有 `.codegraph`）`codegraph sync .`。
