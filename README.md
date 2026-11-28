> ## 星穹旅驿出品 · Chaos Survival Project
> 
> 本插件/模组由 **星穹旅驿（Chaos Survival Project）** 团队开发维护。
> 遇到问题或有功能建议？请到 **GitHub** 提交 Issue 反馈，我们会尽快处理。
> This plugin/mod is developed by **星穹旅驿 (Chaos Survival Project)**. Found a bug or have a suggestion? Please report it on [GitHub Issues](https://github.com/ChaosSurvivalProject/MoreVehicles/issues).
> 
> 🔗 GitHub 仓库 / Repo: [https://github.com/ChaosSurvivalProject/MoreVehicles](https://github.com/ChaosSurvivalProject/MoreVehicles) ｜ 反馈 / Issues: [https://github.com/ChaosSurvivalProject/MoreVehicles/issues](https://github.com/ChaosSurvivalProject/MoreVehicles/issues)

---

# More Vehicles（更多载具）

![More Vehicles 宣传图](assets/banner.webp)

> 上图为概念艺术插画（AI 生成），非游戏实机截图。

Minecraft Java 1.21.11 双端载具系统：服务端权威物理 + 客户端表现增强。
矿车互联成编组、可控动力车、基于 Nadal L/V 判据的脱轨、驾驶座一键装配整台载具，
以及**空中 / 水面 / 水下共用一套 6DOF 方程**的真实三域物理。

- 服务端：**Purpur 1.21.11** 插件（`server/`，Bukkit/Paper API，编译期用 paper-api）
- 客户端：**Fabric 1.21.11** Mod（`client/`，`environment: client`）
- 公共层：**纯 Java**（`common/`，零 MC 依赖，可 JUnit 单测）
- 协议：PLAY 阶段自定义 payload，通道 `morevehicles:v1`（见 [ProtocolSpec.md](docs/ProtocolSpec.md)）

> 架构铁律：物理只在服务端算；客户端只做输入采集、预测、插值、渲染、HUD。
> Purpur 不能加载 Fabric Mod，Fabric 客户端不承载权威逻辑；不使用 Arclight/混合端。
> 未装 Mod 的 vanilla 客户端通过 Display 实体降级可见载具。

## 三域载具概念演示

> ⚠️ **以下均为概念示意图（AI 生成，带 `AI生成` 水印），并非游戏实机截图。**
> 本项目尚未在真实 Purpur + Fabric 环境运行，因此没有真实画面。图片仅用于示意设计目标，
> 实机验收请按 [AI-PROMPTS.md](docs/AI-PROMPTS.md) 的 `TEST-MV` / `TEST-MV-F` 分阶段进行。

| 轨道 · 矿车编组 | 空中 · 6DOF 飞行器 |
|:---:|:---:|
| ![矿车编组概念图](assets/train.webp) | ![飞行器概念图](assets/aircraft.webp) |

| 水面 · 兴波快艇 | 水下 · 跨域潜艇 |
|:---:|:---:|
| ![快艇概念图](assets/boat.webp) | ![潜艇概念图](assets/submarine.webp) |

对应物理内核见 [MV-SPEC.md](docs/MV-SPEC.md)：轨道编组与 κ 动力系数、Nadal 脱轨判据、
气动力/失速、水面兴波阻力、水下浮力/GM 稳性/附加质量/空化。

## 工程结构

```
more-vehicles/
├─ common/   纯 Java 内核：物理（6DOF / 气动 / 编组 / κ / 脱轨 / 三域流体）+ 协议字节
│            └─ 32 项 JUnit 测试（真空抛体、四元数 10万步、失速、GM、空化、螺旋桨、VarInt…）
├─ server/   Purpur 插件：入口 / 命令 / 配置热重载 / Messenger 协议桥 / 轨道图 / 编组 / 装配 / Display 降级
└─ client/   Fabric Mod：网络握手 / 输入 / 快照缓冲 / 客户端实体 / HUD
docs/
├─ MV-SPEC.md      工程总纲（双端技术方案）
├─ AI-PROMPTS.md   AI 提示词包（P0~P9 + 三域增补）
├─ ProtocolSpec.md 跨端字节布局单一来源
└─ TUNING.md       调参指南（κ / Nadal / 气动 / 三域）
```

## 构建

需要 **Java 21 toolchain**（编译目标）与 **JDK 25 运行 Gradle**（fabric-loom 1.19.4 要求 Gradle 9.8+ 且 JVM 25+）。
仓库已包含 Gradle wrapper（指向 9.9.0，华为云镜像加速）。本地需同时安装 JDK 21 与 JDK 25，
并设置 `org.gradle.java.installations.paths=<jdk21路径>`；运行 wrapper 时用 `JAVA_HOME=<jdk25路径>`。

```bash
./gradlew build                  # 三模块编译 + 全部单测
./gradlew :common:test           # 仅纯 Java 内核测试（无需 MC 依赖）
./gradlew :server:build          # 仅服务端插件
./gradlew :client:build          # 仅 Fabric Mod（首次会下载 Minecraft）
```

若本机 toolchain 不在默认路径，设置 `org.gradle.java.installations.paths=/path/to/jdk21`。

## 部署

1. 将 `server/build/libs/more-vehicles-*.jar` 放入 Purpur 1.21.11 服务端的 `plugins/`。
2. 启动一次生成 `plugins/MoreVehicles/config.yml` 与 `blocks.yml`，按需调参（见 [TUNING.md](docs/TUNING.md)）。
3. 客户端安装 Fabric Loader 0.20.1 + Fabric API 0.141.6+1.21.11，
   将 `client/build/libs/more-vehicles-client-*.jar` 放入 `mods/`。
4. 进服后客户端主动发 `hello`，服务端回 `hello_ack`，之后下发快照。
   `/mv debug` 可查看握手状态。

## 命令

`/mv version | couple | uncouple | rerail | dynacurve | reload | blueprint | spawn | debug | ballast`
权限节点：`mv.use`（默认）、`mv.admin`（OP，可改物理参数）。

## 测试现状（诚实口径）

- ✅ `common` 层 32 项 JUnit 全部通过：`./gradlew :common:test`。
- ✅ 服务端模块针对 paper-api 1.21.11 编译通过。
- ⚠️ P1~P9 的真实跨端联调（hello/hello_ack 双向、vanilla 降级、50 载具 20 TPS、
  入水 100 次无 NaN 等）需要实际 Purpur 服务端 + Fabric 客户端环境验证；
  本项目按提示词包提供分阶段验收口径，见 [AI-PROMPTS.md](docs/AI-PROMPTS.md) 的 `TEST-MV` / `TEST-MV-F`。

## 许可

MIT — 见 [LICENSE](LICENSE)。
