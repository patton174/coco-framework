# 受信公共 API 兼容性 Shadow 门禁路由

## 状态

- 当前为 Phase 1 dormant 协议。唯一受信 status 为 `API compatibility trusted shadow`。
- 该 context 不是 required check，不参与自动合并，也不改变 `CI gate`、`Agent jury gate` 或 `Agent issue gate`。
- 开关只有 repository variable `COCO_API_COMPATIBILITY_TRUSTED_SHADOW`。缺失或非精确 `true` 时，自动 producer、`workflow_run` bind、verify 和 publish 都不运行。
- `workflow_dispatch` 只允许受保护分支对 policy bundle 做 dormant preflight；它不生产候选、不发布 status，也不能替代 canary。

## 信任边界

候选 producer 只从 `pull_request` 或 `merge_group` 运行。它拥有 `contents: read`，没有 secrets、environment 或 write permission；fork checkout 必须使用 event 所给的 fork repository 和 exact head SHA。

producer 只能执行候选 Maven build，并上传短期 artifact：顶层 `manifest.json` 加严格 32 个 normalized `jars/<artifactId>.jar`。build 固定 candidate version `2.0.2-SNAPSHOT`；stage 从 reactor 的 versioned `<artifactId>-<version>.jar`、sidecar 和 JAR 内嵌 Maven descriptors 取得 GAV，复制为 bare name。manifest v3 对每项记录 group/artifact/version、原 source path、normalized filename、size 和 SHA-256；它是非权威声明，protected verifier 必须逐项重算。producer 不 checkout protected code，不运行 checker，不生成或上传 XML、proof、verdict、report、POM 或 policy 文件。candidate checkout 在 checkout、build、stage 和 upload 前后都必须保持 exact HEAD、clean index、clean tracked 文件和没有 nonignored untracked 文件。

候选 Maven/POM、manifest、JAR 名称和 archive 内任意文本不授予语义结论。producer job 永远不是 `CI gate` 的 dependency。

## 受保护验证

`API Compatibility Trusted Shadow` 仅由默认分支的 `workflow_run` 执行，并只消费工作流名和路径均为 `CI` / `.github/workflows/ci.yml` 的 completed source run。每个 bind、verify 和 publish 都重新检查：

- event snapshot、source run ID、attempt、workflow ID/name/path、repository、source event、head SHA 和当前 protected `main` head；
- PR 必须唯一、open、base 为 `main`，且 API 的 head repository/ref/SHA 与 run 一致；fork 只按其 own repository 和 exact SHA 读取；
- merge queue 必须是 `gh-readonly-queue/main/...` synthetic ref；
- 同一 event/head 的更高 run ID 或 attempt 会拒绝旧运行；publish 也拒绝覆盖更高 owned shadow status；
- protected checkout 在 checkout、policy read、artifact verify 和 publish 前后均检查 HEAD、index、tracked 与 nonignored untracked clean。

`verify-jars` 从 exact protected SHA checkout 并重新计算 canonical JSON policy bundle：

- `public-api-profile.json` schema v3 是 32 项 inventory 的唯一权威来源；每项精确定义 `modulePath`、`groupId`、`artifactId`、normalized `jarName`、`baselineState` 与 `comparison.targetArtifactId`。状态严格为 20 个 `present` 和 12 个 `missing`；20 个 comparison 中恰有 10 个 direct replacement，target 必须存在并自指，禁止 chain/cycle；
- `baseline-sha256.json` schema v3 必须按 artifactId 排序覆盖全部 32 项。`present` 项只含精确 POM/JAR size 与 SHA-256；`missing` 项只含精确 POM/JAR 404 status，不允许伪 digest。Maven Central URL 由 root origin/group/version 和 artifactId 推导，必须精确匹配 `https://repo.maven.apache.org/maven2/<group>/<artifact>/<version>/<artifact>-<version>.jar`；
- `allowlist.json` schema v3 的每项规则必须是唯一排序的精确 `(artifact, class, member, category, reason)`；class-wide finding 使用 `member="<class>"`。禁止通配、artifact-wide 或 `MODIFIED` broad bypass；受保护 verifier 解析自己生成的 japicmp XML 后逐 finding 应用规则，同 artifact 的任意其他不兼容仍阻断；
- `japicmp-policy.json` schema v3 同时固定 finding key/categories、API checker 使用的 `mavenPlugin` 和 shadow 使用的 `cli`。Maven plugin 固定 size `44670` 与 SHA-256 `7df259e8be0c652259ef96416fcc6f2e7ef5e5a340a4df52783350abcd77c4bb`；CLI fat JAR 固定 size `5988558` 与 SHA-256 `f2300dd9b8aca31c49a95dfad5a6794b4475f4e83809ad69f8f1e11d87014657`；两者 URL 均只能是精确 `repo.maven.apache.org` 路径；
- API checker 与 shadow 从 protected policy directory 导入同一个 `policy_bundle.py`。schema v3 bundle hash 对 profile、baseline ledger、allowlist、japicmp policy 与 raw signing-key SHA 做同一 canonical normalization；两端必须产生同一 normalized object 与 golden hash；
- 任何 asset 缺失、symlink、非 canonical JSON、schema 偏差、inventory/ledger 偏差或 pin 偏差都 fail closed。基础 assets 合并前，路由保持 dormant，不能启用 shadow。

在下载候选 artifact 或进入 baseline 分支前，verifier 通过 GitHub API 从候选 own repository 的 exact candidate SHA 读取 profile 每个 `modulePath/pom.xml`，只把 POM 字节当作不受信数据交给 protected parser，不 checkout 或执行候选代码。每个 module POM 的 effective group/artifact/fixed revision 必须与 protected profile 一致；manifest 的 `source_path` 也必须精确等于 protected `modulePath/target/<artifactId>-<fixed-version>.jar`。producer 提供的 source path 从不决定模块映射。

verifier 独立下载 pinned japicmp fat JAR，禁用 proxy、redirect 和 cache，复核 final URL、size/hash，然后安全解析 artifact，不信任 producer manifest 来决定 inventory。GitHub outer artifact 必须先验证唯一 canonical `Content-Length`，再以 64 KiB chunk、上限加一探测和增量 SHA-256 流式写入临时文件；缺失/重复/非数字/零/超限 length、截断、额外字节、read/ZIP 失败都 fail closed 并清理临时文件。archive 必须且只能包含 profile 推导的 32 个 JAR 和一个 manifest；缺失、额外、重复、case collision、path traversal、backslash、symlink、加密 entry、损坏 ZIP、压缩炸弹、coordinate/JAR 交换都失败。每个 inner JAR 还独立限制 entry count、单 entry/总展开大小、压缩比、路径和 duplicate/case collision。

所有 32 个 inner JAR（包括 12 个 missing-baseline artifact）都必须在 `META-INF/maven/<groupId>/<artifactId>/` 同目录下恰有一份 `pom.properties` 和一份 `pom.xml`；该 prefix 任意深度的额外 descriptor 都拒绝。POM XML 禁止 DTD/entity，`artifactId` 必须是 project 直属字段，`groupId/version` 只能是直属字段或唯一 parent fallback。descriptor path、properties、XML、manifest GAV、protected profile coordinate、fixed candidate version 和 normalized filename 必须全部精确一致且全局唯一；filename 本身从不构成坐标证据。内嵌 descriptors 只能证明 artifact identity，不能证明 source reproducibility；后者需要独立的可复现构建协议，不由本 shadow 路由声称。

验证器对 20 个 present baseline 逐项用真实 japicmp 0.23.1 重跑 API/ABI 语义；candidate 必须由该项 `comparison.targetArtifactId` 选择，不能盲用同名 facade。固定 CLI flags 为 `--error-on-binary-incompatibility` 与 `--error-on-source-incompatibility`；JVM 固定 `-Xmx512m -XX:MaxMetaspaceSize=192m`，单 artifact 最长 60 秒，`verify-jars` job 最长 30 分钟。兼容比较必须 exit 0；不兼容比较 exit 1 且必须产生可严格解析的 protected XML findings。未知参数、缺 XML、其他 exit code 或超时 fail closed；producer XML 永远不是输入。

## 权限与发布

- `bind` 与 `verify-jars` 仅有 `actions: read`、`contents: read`、`pull-requests: read`；没有 `statuses: write`。
- `publish` 是唯一拥有 `statuses: write` 的 job。它不调用 artifact API，不下载、解压或解析 artifact，也不运行 japicmp。
- verifier 到 publisher 的唯一数据为精确 8 ASCII byte token：`PASS0000` 或 `FAIL0001` 到 `FAIL0005`。未知长度、newline 或其他值失败。
- publisher 在 POST 前重新 bind source run/attempt/head，并且只向 exact candidate SHA 发布固定 context `API compatibility trusted shadow`。它绝不发布、更新或冒充 `CI gate`。

## 验收

协议测试覆盖：breaking JAR 加伪 XML、policy/POM 污染、dirty tracked/untracked/index、缺失 policy、31/33 JAR、outer/inner duplicate/case collision/zip bomb/资源上限、stale run/attempt/head、manifest stale/extra/duplicate/wrong-version、missing-baseline JAR 交换、manifest/descriptor 重写、source-path 漂移、exact-head module POM/profile mapping 漂移、replacement missing target/chain/cycle、32-entry ledger present/missing 形状、双工具 pin、bounded response/cleanup、fork、merge group、publisher 非法 token、严格 allowlist 与 Maven Central URL。真实 reactor 测试必须构建全部 32 个 Maven artifact，执行 producer normalized stage，再由 protected profile 的 modulePath/GAV/inventory 验证通过；真实 japicmp 测试必须用 JDK 编译 compatible/removed-method JAR，证明 0.23.1 分别 exit 0/1、精确 `REMOVED` finding 规则通过且同 artifact 其他 breaking finding 仍失败。每次路由变更还必须执行 Python 编译、Ruff、YAML 解析、可用 governance tests、`git diff --check` 和 `codegraph sync .`。
