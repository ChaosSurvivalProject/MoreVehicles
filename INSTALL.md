# More Vehicles — 安装说明

本项目提供两个可直接部署的构建产物（jar）。请按你的服务端类型选择对应版本。

## 产物清单

| 文件 | 目标环境 | 说明 |
|------|----------|------|
| `more-vehicles-server-0.1.0.jar` | Purpur 1.21.11 服务端 | 服务端插件，已内嵌 common 物理内核，无需额外依赖 |
| `more-vehicles-client-0.1.0.jar` | Fabric 1.21.11 客户端 | 客户端 Mod，已内嵌 common 内核（Jar-in-Jar） |

> 版本 0.1.0 · Minecraft 1.21.11 · Java 21 · 协议版本见 `docs/ProtocolSpec.md`

## 服务端安装

1. 准备一个 **Purpur 1.21.11** 服务端（可从 https://purpurmc.org 获取）。
   - 本插件编译期使用 paper-api，Purpur 是其 API 超集的直接 fork。
   - **不支持** Arclight / Mohist 等混合核心，也不支持服务端 Fabric。
2. 将 `more-vehicles-server-0.1.0.jar` 放入服务端的 `plugins/` 目录。
3. 启动服务端，插件会生成 `plugins/MoreVehicles/config.yml` 与 `blocks.yml`。
4. 调参后执行 `/mv reload` 热重载配置。

**命令**：`/mv`（查看帮助）、`/mv reload`（热重载）、`/mv spawn <type>`、`/mv list`、`/mv remove <id>`。

## 客户端安装

1. 安装 **Fabric Loader 0.20.1**（针对 Minecraft 1.21.11）。
2. 安装 **Fabric API 0.141.6+1.21.11**。
3. 将 `more-vehicles-client-0.1.0.jar` 放入 `.minecraft/mods/` 目录。
4. 启动游戏并连接装有本插件的服务端；客户端会自动握手协商协议版本。

> 客户端 Mod **单向依赖**服务端插件。若服务端未安装，Mod 会静默降级为 vanilla 行为，不会导致崩溃。

## 从源码构建

需要 JDK 21（编译目标）+ JDK 25（运行 Gradle + fabric-loom）。仓库已包含 Gradle wrapper（9.9.0）。

```bash
./gradlew clean build
# 产物：
#   server/build/libs/more-vehicles-server-0.1.0.jar
#   client/build/libs/more-vehicles-client-0.1.0.jar
# 运行测试：
./gradlew :common:test
```

详见 `README.md`、`docs/MV-SPEC.md`（工程总纲）、`docs/ProtocolSpec.md`（协议规范）、`docs/TUNING.md`（调参指南）。
