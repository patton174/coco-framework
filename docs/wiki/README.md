# docs/wiki：GitHub Wiki 的源目录

本目录是 Coco Framework 公开 [GitHub Wiki](https://github.com/patton174/coco-framework/wiki) 的**唯一事实源**。目录里的页面经 `.github/scripts/wiki_sync.py` 单向镜像到 Wiki 仓库；这个 `README.md` 只在主仓库里说明规则，**不会**被镜像。

> 真实 Wiki 在维护者完成首次手动发布之前不可用，上面的链接会先显示 404。发布步骤见 [Guide-Contribute-To-Wiki.md](Guide-Contribute-To-Wiki.md)。

## 为什么事实源在主仓库

GitHub Wiki 是独立的 Git 仓库，没有分支保护、CODEOWNERS 和 CI。直接在网页上编辑会绕开 PR 评审，并且下一次同步会覆盖这些修改。所以所有改动都在这里提交，经过 PR 评审后再由维护者发布。

## 目录规则

- 目录保持扁平，不建子目录，只放 `.md` 文件，不放隐藏文件。
- 页面文件名使用 ASCII 的 `Title-Case-With-Hyphens.md`，例如 `Getting-Started.md`、`Module-Web.md`。
- 特殊页必须存在：`Home.md`（首页）、`_Sidebar.md`（侧栏，链接每一个内容页）、`_Footer.md`（页脚，包含仓库、CHANGELOG、CONTRIBUTING、LICENSE 链接）。
- 页面之间用 `[文字](Page-Name)` 链接，不带 `.md`、目录或 `../`；指向仓库文件时使用 `https://github.com/patton174/coco-framework/blob/main/<路径>` 绝对地址，目录使用 `/tree/main/`。
- 文件使用 UTF-8（无 BOM）、LF 换行，末尾恰好一个换行；不使用 YAML front matter；代码围栏必须成对，允许 Mermaid。
- 公开卫生：不写本机路径与机器现象、不写邮箱、密钥只写键名；不留未决标记，文档与实现不一致时写“文档与实现存在出入：…，以 <来源> 为准”。
- 本文件 `README.md` 是唯一允许存在、但不镜像、也不参与校验的文件。

## 本地校验与发布

```powershell
# 校验页面集合、链接、锚点、围栏、卫生与密钥形态（失败时以非零退出并给出 文件:行号）
python .github/scripts/wiki_sync.py check

# 脚本自身的单元测试
python .github/scripts/test_wiki_sync.py
```

`validate` 是 `check` 的别名。校验通过后，维护者手动发布：先把真实 Wiki 克隆到主仓库之外，再运行 `publish`。它只会新增、更新、删除 Wiki 页面，保留 `.git/`，在 Wiki 克隆里创建一个本地提交，**不会推送**。

```powershell
python .github/scripts/wiki_sync.py publish ..\coco-framework.wiki --dry-run
python .github/scripts/wiki_sync.py publish ..\coco-framework.wiki
git -C ..\coco-framework.wiki push origin HEAD   # 维护者手动执行
```

只想镜像文件、不创建提交时使用 `sync --target <目录>`（支持 `--dry-run`、`--check`、`--no-prune`）。相同内容重复发布不会产生新的差异或提交。

## 修改流程

1. 在本目录修改或新增页面；新增页面要同步 `_Sidebar.md` 与 `Home.md`。
2. 运行上面的 `check` 与单元测试，通过后再提交。
3. 按 [CONTRIBUTING.md](../../CONTRIBUTING.md) 提 PR，并勾选 PR 模板中的 Wiki 与 CHANGELOG 项。
4. 合并后由维护者手动发布；Agent 与 CI 不推送 Wiki。
