# Coco Feature 生命周期

## 目标

Coco 的 feature 机制同时影响依赖组合、自动配置和最终 Spring Boot 包。构建期与运行期必须使用同一份标准 feature 模型，避免“配置显示已关闭，但依赖或 Bean 仍然存在”的分裂状态。

当前标准 feature 为：

- `web`
- `security`
- `audit`
- `mybatis-plus`
- `tenant`
- `data-permission`
- `openapi`

源码生成不是运行时 feature。需要开发期脚手架时使用独立的 [coco-generate](https://github.com/patton174/coco-generate) 仓库。

## 1. 声明

业务项目可以通过以下入口声明启用或禁用项：

- `application.yml`、`application.yaml` 或 `application.properties` 中的 `coco.features.enabled` 和 `coco.features.disabled`；
- `@CocoFeatures`；
- Maven 参数 `coco.features.enabled` 和 `coco.features.disabled`；
- 兼容期保留的 `CocoConfigurer` Bean，仅在没有构建期清单的运行场景参与回退解析。

同一 feature 同时出现在 enabled 和 disabled 时属于冲突输入，应失败而不是静默选择一侧。

## 2. 解析

`coco-feature-model` 保存标准 feature 的标识、默认状态、Maven 制品、自动配置类、依赖关系和需要裁剪的关联制品。

解析器按以下顺序得到最终计划：

1. 加载默认启用项；
2. 合并显式启用和禁用声明；
3. 应用显式禁用；
4. 递归移除依赖已不完整的 feature；
5. 产出 enabled、disabled 和 disabled-by-dependency 集合。

例如 `tenant` 和 `data-permission` 依赖 MyBatis-Plus 与安全上下文；依赖项关闭后，它们不能继续保持启用。`openapi` 依赖 Web 与安全上下文。

## 3. 构建期清单

继承 `coco-parent` 的业务项目会执行 `coco:features`。该 goal 读取资源配置、Maven 参数和可扫描的 `@CocoFeatures` 声明，使用标准模型计算计划，然后：

- 生成 `META-INF/coco/features.json`；
- 把启用 feature 的制品加入 Maven 项目模型；
- 从构建依赖集合中移除已禁用 feature 及其声明的关联制品。

清单记录的是已经完成依赖传播后的最终状态，不是待运行时再次合并的原始配置。

## 4. 打包裁剪

Spring Boot 完成 repackage 后，`coco:prune-package` 读取同一份清单，并从可执行包中移除禁用 feature 的 jar。裁剪还必须同步更新 `BOOT-INF/classpath.idx` 和 `BOOT-INF/layers.idx`，不能只删除 `BOOT-INF/lib` 文件。

构建验证至少应检查：

- 清单中的 enabled/disabled 状态符合依赖传播结果；
- 禁用 feature 的 Coco 制品不在 `BOOT-INF/lib`；
- feature 声明的第三方关联制品按规则裁剪；
- classpath 和 layer 索引不再引用已删除文件。

## 5. 运行期激活

`coco-spring-boot-autoconfigure` 启动时优先读取类路径中的构建期清单，并直接恢复最终 feature 计划。只有清单不存在时，才回退到属性、`@CocoFeatures` 和 `CocoConfigurer` 的运行期合并。

`@ConditionalOnCocoFeature` 和自动配置导入过滤器读取该计划，决定 feature Bean 和自动配置是否进入应用上下文。业务代码可以查询 feature manager，但不应在运行中修改已解析计划。

## 6. 一致性要求

任何 feature 变更都必须同时覆盖以下位置：

1. `coco-api` 的公开 feature 标识；
2. `coco-feature-model` 的元数据、依赖和裁剪声明；
3. 对应 feature 制品与自动配置；
4. starter 依赖组合；
5. Maven plugin 的清单和裁剪测试；
6. starter 集成测试和用户文档。

只修改其中一层会造成构建和运行行为不一致，应视为阻断性回归。
