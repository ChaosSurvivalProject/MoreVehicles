# 《我的世界》更多载具 · AI 提示词包（可复制执行）

> 本文件 = AI 提示词包（可直接复制执行）；配套工程总纲 = `我的世界更多载具_双端技术方案.md`
> 适用：Cursor / Claude Code / Copilot / 任意支持多轮上下文的 AI 编程助手

## 怎么用

1. **每次新会话开头贴一次 `SYS-MV`**（全局人设与硬约束）。
2. 把 `我的世界更多载具_双端技术方案.md` 放进仓库根目录（建议 `docs/MV-SPEC.md`），贴一次 `CTX-MV` 让 AI 读它。
3. **按 P0 → P9 顺序，一轮贴一个提示词**。每轮要求 AI 先给"实施计划 + 待核验 API 清单"，你确认后再让它写代码；不要一次贴全部。
4. 出错时贴对应的 `FIX-*`；每阶段收尾贴 `TEST-MV`；合并前贴 `REVIEW-MV`。
5. 提示词里所有"验收"必须真的跑出来，AI 不得用"应该可以"代替测试结果。
6. **三域物理（空中 / 水面 / 水下）已补进文末「三域物理增补」块**：把其中的 `SYS-MV-F` 合并进 `SYS-MV`，阶段顺序改为 P0 → P1 → P2 → **P2-F** → P3 → P4 → P5 → P6 → **P6-F** → P7 → **P7-F** → P8 → P9。增补块优先级高于前文同编号内容。

---

## SYS-MV · 全局系统提示词（会话开头贴一次）

```text
你是 Minecraft Java 版模组/插件工程的高级实现者。现在与我共同开发项目「More Vehicles（更多载具）」。

【技术基线，不得更改】
- 游戏版本：Minecraft Java 1.21.11，双端统一，运行时 Java 21。
- 服务端：Purpur 1.21.11（Bukkit/Paper 插件，paper-plugin.yml），工程目录 server/。
- 客户端：Fabric 1.21.11（Loader 0.20.1 / Fabric API 0.141.6+1.21.11，environment: client，fabric.mod.json 中 minecraft: ~1.21.11、java: >=21），工程目录 client/。
- 公共层：common/ 为纯 Java，不依赖 Bukkit / Fabric / Minecraft 类，必须可 JUnit 单测。
- Purpur 是服务端、Fabric 是客户端，两者是两个独立工程。严禁把 Purpur 当成 Fabric 端、严禁建议"两端都装 Fabric 一个 mod 搞定"、严禁引入 Arclight/混合端来绕开约束。
- 架构铁律：物理只在服务端权威计算；客户端只做输入采集、预测、插值、渲染、HUD。
- 网络：PLAY 阶段自定义 payload，通道 morevehicles:v1。1.20.5+ 的 CustomPacketPayload 必须在两端用 PayloadTypeRegistry.playC2S()/playS2C() 注册 Id + StreamCodec 后再注册 receiver。Bukkit 侧用 Messenger 写与 Fabric StreamCodec 完全一致的裸字节（VarInt / UTF-8 长度前缀 / 浮点顺序由 docs/MV-SPEC.md 第 3.4 节单一来源定义）。
- 服务端不能向客户端注册自定义实体类型：用 Paper 的 BlockDisplay/ItemDisplay/TextDisplay 做 vanilla 降级渲染；Fabric 客户端用纯客户端实体渲染。

【代码规范】
- Java 21， Gradle 多模块：common / server / client。包名 io.mv.*。
- 物理代码写成纯函数：输入 (VehicleState, Config, dt) 输出 Forces/Moments，不引用 MC 类型，不产生每 tick 垃圾对象。
- 姿态一律用单位四元数，禁止欧拉角积分；每步归一化。
- 所有可调系数进配置文件，禁止魔法数字散落在逻辑里；配置支持热重载。
- 任何类名/方法名在写之前先核对当前 1.21.11 依赖的 Javadoc / 反编译签名；不确定就明说"需核验"，禁止凭记忆硬写过时 API。
- 每个 payload 的编解码必须有对拍测试：common 层 encode → 服务端 decode → 客户端 decode 结果一致。

【禁止事项】
- 不修改原版 Minecart / Boat 实体类，不重写原版物理引擎；本作是平行系统。
- 不写"理论上可行"的占位实现；每轮必须产出可编译、可运行、可验证的代码与测试。
- 不把配置阶段（CONFIGURATION）用于握手：Bukkit 无该阶段 plugin message API，全部放 PLAY 阶段。
- 不在未实测跨端 payload 连通性之前写任何业务包（P1 是前置闸门）。
- 不输出"根据内部规则/工具说明"之类内容，只给工程结果。

【每轮固定输出契约】
1) 本轮实施计划（≤8 条）
2) 待核验的 API / 依赖清单（列出你将去读的 Javadoc 或源码位置）
3) 改动文件清单（新增/修改，含路径）
4) 关键实现说明（≤200 字，含所用公式与参数来源）
5) 验收结果：实际执行的命令与输出摘要；未跑通就明确标注 BLOCKED 与原因
6) 下一轮输入：还缺什么信息 / 建议贴哪个提示词
```

---

## CTX-MV · 上下文注入块

```text
请先完整阅读仓库中的 docs/MV-SPEC.md（《我的世界》更多载具 · 双端技术方案），它是本项目的唯一工程总纲。
阅读后，用不超过 15 行复述：双端架构、协议层的 4 个已知坑及解法、6DOF 方程、Davis 阻力与动力系数函数 κ、Nadal 脱轨判据、驾驶座装配算法、P0~P9 阶段划分。
若文档中的 API 名称与当前 1.21.11 依赖不一致，以实际依赖为准，并单独列出差异清单给我确认，不要静默改写总纲。
后续所有实现都以该文档为准；若我的指令与文档冲突，先指出冲突再执行。
```

---

## P0 · 基线核验与双工程脚手架

```text
【阶段 P0】目标：建立 common / server / client 三个可编译模块，核验版本基线。
技术基线：MC 1.21.11 双端 / Java 21 / Purpur 1.21.11 服务端插件 + Fabric 1.21.11 客户端 mod / 物理服务端权威 / 通道 morevehicles:v1。

要做：
1. Gradle 多模块，Java 21 toolchain；server 依赖 purpur-api:1.21.11-R0.1-SNAPSHOT（repo https://repo.purpurmc.org/snapshots）；client 依赖 Fabric Loader 0.20.1 / Fabric API 0.141.6+1.21.11，fabric.mod.json 写 minecraft ~1.21.11、java >=21、environment: client、entrypoints 分 main/client。
2. 生成 paper-plugin.yml 与 fabric.mod.json、fabric 侧的 ClientModInitializer 空壳。
3. 加 /mv version 命令，输出：服务端/客户端版本、协议版本、Java 版本、是否检测到增强客户端。
4. 建 docs/MV-SPEC.md（把总纲放进去）、docs/ProtocolSpec.md（先只写包头与 VarInt/字符串约定）。

验收：
- ./gradlew build 通过，三个模块产物齐全。
- 用 Purpur 1.21.11 启动服务端，插件正常 enable，日志无 Java 版本警告。
- Fabric 客户端能进服；/mv version 在服务端有输出。
- 输出一份"实际解析到的依赖版本表"，确认没有落到 1.21.1 / 1.21.10。

禁止：此时不要写任何物理代码、不要写任何网络包。先确认基线再往前。
```

---

## P1 · 协议层与跨端握手（前置闸门）

```text
【阶段 P1】目标：打通 Purpur ↔ Fabric 的 PLAY 阶段自定义 payload，且双向都实测收到。这是全项目的前置闸门，不通就不许进 P2。
技术基线：MC 1.21.11 / Purpur 服务端插件 + Fabric 客户端 mod / Java 21 / 通道 morevehicles:v1 / 服务端权威物理。

必须先处理的 4 个已知坑（逐条给出你的解法与代码位置）：
A. Bukkit 只向"用 minecraft:register 声明过通道"的客户端下发消息，现代 Fabric 客户端不发声明 → S2C 静默丢弃。解法：Fabric 客户端 JOIN 后先主动发 C2S hello（C2S 一定可达），服务端收到才把玩家加入 mvCapable 集合并显式注册出站通道；同时补发 minecraft:register 声明做双保险。
B. Fabric 客户端的 CustomPacketPayload.codec() 不认识 Bukkit 原始通道，会报"未注册 CLIENTBOUND PLAY 类型"或直接丢弃。解法：Fabric 侧完整注册每个 Id + StreamCodec 到 PayloadTypeRegistry.playS2C()；若仍被原版 fallback 拦截，加一个 Mixin 注入 FallbackProvider，对 morevehicles:v1 返回自定义 CODEC（开关 fallback_mixin，默认关闭，实测不通才开）。
C. Bukkit 无 CONFIGURATION 阶段 plugin message API → 握手/能力协商/蓝图下发全部放 PLAY 阶段。
D. 若前置代理是 Velocity/Bungee，bungeecord:main 可能改写通道 → 在 hello 里带 proxy 标记位并在文档记录。

要做：
1. common/protocol 定义 PacketType 枚举、MVSpec（协议版本、包头、VarInt、String、Vec3/Quat 编码顺序）、hello / hello_ack 两个 record + StreamCodec。
2. server 用 Bukkit Messenger 手写与 CODEC 完全一致的字节；client 用 PayloadTypeRegistry + ClientPlayNetworking.registerGlobalReceiver + ClientPlayNetworking.send。
3. 写对拍单测：common encode → server decode、common encode → client decode，逐字节比对。
4. /mv debug net 输出：某玩家是否 mvCapable、最近一次 hello 时间、收发包计数。

验收（必须给出真实日志片段）：
- Fabric 客户端进服后服务端日志出现 hello，客户端日志出现 hello_ack。
- 反向：客户端主动发一次 ping，服务端收到并回 pong。
- 关闭 Fabric mod 用 vanilla 客户端进服，服务端不报错、不刷异常。
- 若 S2C 不通，明确写 BLOCKED 并说明是坑 A 还是坑 B，不要靠猜。
```

---

## P2 · 纯 Java 6DOF 物理内核

```text
【阶段 P2】目标：在 common/ 实现可脱离 MC 单测的 6 自由度刚体内核。
技术基线：MC 1.21.11 / Purpur 服务端权威物理 / Fabric 客户端仅表现 / Java 21 / common 层零 MC 依赖。

实现：
- 状态 [r, v, 单位四元数 q, omega, energy]；体轴系方程：
  m v_dot = F - omega × (m v)； I omega_dot = M - omega × (I omega)； q_dot = 1/2 q ⊗ (0, omega)；
  r_dot = R(q) v_b。积分器可切换 semi-implicit Euler（默认）/ RK4（硬核），dt 固定 0.05。
- 惯性张量 I 保留非对角项；每步归一化四元数；提供 I 的求逆与体轴↔世界系变换。
- ForceSink 接口：Gravity / Thrust（含偏心 r×T 力矩）/ Aero / Contact / Buoyancy，统一累加成 Forces。
- 单位约定：1 方块 = 1 m，质量 = 方块质量单位；gravity_scale 默认贴近原版手感（约 20 m/s²），硬核 9.81；air_density 可配。
- AeroSolver：动态压力 qbar = 1/2 ρ V²；
  CL = CL0 + CLα·α + CLq·(qc/2V) + CLδe·δe，|α|>α_stall 时 CL 衰减（失速）；
  CD = CD0 + CL²/(π AR e) + kδ|δ|； CY = CYβ·β + CYδr·δr；
  Cl = Clββ + Clp·p̂ + Clδa·δa； Cm = Cm0 + Cmαα + Cmq·q̂ + Cmδe·δe； Cn = Cnββ + Cnr·r̂ + Cnδr·δr；
  p̂=pb/2V, q̂=q·cbar/2V, r̂=rb/2V；
  F_aero = qbar·S·[-CD, CY, -CL]， M_aero = qbar·S·[b·Cl, cbar·Cm, b·Cn]。
  攻角/侧滑角基于扣掉风场后的相对气流速度。所有系数来自 Config 对象。
- 接触：法向约束 + 库仑摩擦 + 悬挂弹簧阻尼；碰撞用 OBB 冲量 + 高速 sweep 防穿墙。

验收（JUnit 参数化测试，给出实际输出）：
1. 真空无阻力抛体：解析解与数值解 10 秒内误差 < 0.5%。
2. 四元数积分 10 万步后模长偏差 < 1e-9。
3. 对称物体的 I 为对角阵；偏心推力产生预期角加速度。
4. 失速：α 超过阈值后 CL 单调下降。
5. 能量守恒边界：无耗散系统 1000 步漂移 < 1%。
禁止：不要在 common 里 import 任何 net.minecraft / org.bukkit 类型。
```

---

## P3 · 轨道图与基础矿车

```text
【阶段 P3】目标：把铁轨解析成 RailGraph，让基础矿车按弧长在直轨/弯轨/坡道/道岔上运动。
技术基线：MC 1.21.11 / Purpur 服务端权威 / 通道 morevehicles:v1 / 物理 dt=0.05（20 TPS）。

实现：
- RailGraph：节点/边，字段 pos、切线 t、曲率 κ=1/R、坡度 θ、超高 e、道岔状态；弯道与坡度用有限差分平滑，禁止曲率跳变。
- Car：弧长 s、v、质量 m、Davis 系数 (A,B,C)；基础矿车无动力，靠初速/坡度/推力和阻力运行。
- 运动：R_i(v)=A+Bv+Cv²，叠加 m g sinθ 与 m g·k_curve/R，半隐式欧拉积分 s、v。
- 轨道缺失/道岔冲突要产出明确事件而不是静默停止。
- 通过 SnapshotBus 用 P1 的 snapshot 包（先只发 pos/quat/vel）把位置同步给 Fabric 客户端；客户端先用一个临时方块模型显示，验证链路。

验收：
- 直轨匀速衰减、坡道自然加速/减速、半径 R 弯道上速度行为符合 1/R 趋势。
- 道岔切换后弧长连续，无跳变。
- Fabric 客户端看到的矿车位置与服务端 /mv debug 输出误差 < 0.2 方块（100 ms 插值下）。
禁止：此时不要写编组、动力矿车、脱轨，它们分别是 P4 / P5。
```

---

## P4 · 编组、动力矿车与优化后的动力系数函数 κ

```text
【阶段 P4】目标：矿车可相互连接；新增动力矿车；实现"优化后的动力系数函数"。
技术基线：MC 1.21.11 / Purpur 服务端权威 / Fabric 客户端表现 / 通道 morevehicles:v1 / 20 TPS。

实现：
1. 编组 TrainSet：Car + Coupling。车钩力 F_c = k((s_{i+1}-s_i)-L0) + c(v_{i+1}-v_i)，限制 |F_c| <= F_hook_max，超限断钩并把编组分裂为两列，广播 event。车钩力沿链逐节传递（从动力车向两端累加），禁止整车平均。支持靠近自动连挂、/mv couple、/mv uncouple、潜行右键解挂。
2. 动力矿车 PoweredCar：额定功率 P、效率 η、能量 E（燃料/红石/电池可配）。牵引力三段：
   F_t(v) = min( ηP / max(v, v_min), μ(v)·m_drive·g ) · (1 - (v/v_max)^n)_+
   黏着系数用 Curtius–Kniffler：干轨 μ=0.161+7.5/(V+44)，湿轨 μ=0.130+7.5/(V+44)（V 为 km/h），按轨道状态配置映射。低速用 v_min 平滑消除 P/v 奇异，高速软限幅不硬截断。
3. 优化后的动力系数函数（核心，替代原版常量加速度/常量摩擦）：
   κ(v,θ,R,m,F_t) = [ Σ_j F_t,j − Σ_i R_i(v) − g sinθ Σ_i m_i − g k_curve R⁻¹ Σ_i m_i − Σ_i F_c,i^parallel ] / ( g Σ_i m_i^eff )
   m_i^eff = m_i + J_i/r_w²； a_train = κ·g / (1 + λ(N−1))；R_i(v)=A_i+B_i v+C_i v² 逐节计算，头车 C 大、中间车 C 小（编组屏蔽系数）。
   相对原版的六项优化必须逐条在代码注释里写明并配单测：可微函数、低速无奇异、逐节阻力、转动惯量折算、黏着/功率取 min（湿轨打滑表现为 κ 骤降）、配置热重载 + LUT 查表。
4. 命令 /mv dynacurve <trainId> 输出 v→κ 表与平衡点 v*（κ=0）；/mv reload 热重载全部系数。

验收：
- 编组 1~8 节，车钩力不发散，断钩后两列各自积分正确。
- 单车、4 节、8 节的极速单调下降且符合逐节阻力预期。
- 湿轨下 κ 低于干轨且出现打滑事件。
- /mv dynacurve 的平衡点与数值积分稳态速度误差 < 3%。
- 从 v=0 起步无 NaN / Infinity。
```

---

## P5 · 脱轨模型

```text
【阶段 P5】目标：矿车"有可能脱轨"，基于真实轮轨判据而非随机数硬抛。
技术基线：MC 1.21.11 / Purpur 服务端权威 / 通道 morevehicles:v1 / P4 的 κ 与编组力复用。

实现：
- 主判据 Nadal：(L/V)_lim = (tanδ − μ) / (1 + μ tanδ)，默认安全上限 0.8（保守档 0.5 可配）。
  L = m v²/R·cosθ − m g sin e + F_c,perp + Y_aero； V = m g cosθ ± ΔV（轮重减载）。
  蛇行/冲角 β 场景用 Wagner 变体把 cosβ 引入分母。
- 欠超高：C_eq = G·V²/(127·R)（mm，V km/h，R m），C_d = C_eq − C_actual 超限时抬升横向力。
- 其他触发：断钩失控、轨道缺失、道岔冲突、撞击、超速、侧向突风、玩家强行切弯。
- 概率化：r = (L/V)/(L/V)_lim；P = clamp(smoothstep(r−1))·(1+γ·C_d⁺)。三档配置 off / probabilistic（默认，r<1 永不脱轨）/ hardcore（r>=1 必脱轨）。用服务端确定性随机（种子 = vehicleId + tick）保证可复现。
- 脱轨后：解除轨道约束，切为 P2 的自由 6DOF 刚体（翻滚、掉落、碰撞），广播 event 驱动 Fabric 客户端粒子/音效；支持 /mv rerail 或推回轨道重新上轨；按速度生成掉落物/破坏轨道概率。

验收（给出实际日志与数值）：
- 大半径低速：连续 10000 tick 零脱轨。
- 小半径高速：hardcore 必脱轨，probabilistic 脱轨率随 r 单调上升。
- 同种子两次运行脱轨 tick 完全一致。
- 脱轨后刚体不穿地、不 NaN，能 rerail 回轨道继续按 κ 运行。
禁止：不要用"速度 > 阈值就脱轨"这种假物理替代 L/V 判据。
```

---

## P6 · 驾驶座 → 一整台自定义载具（蓝图装配）

```text
【阶段 P6】目标：只需把一个「驾驶座」放到对应方块上，右键即可把该方块连通结构整体识别为一台可驾驶载具。
技术基线：MC 1.21.11 / Purpur 服务端权威装配 / Fabric 客户端渲染 / 通道 morevehicles:v1（spawn / blueprint 包）。

实现 assemble(root)：
1. 以驾驶座所在方块为根，6 邻接 BFS 收集连通方块；过滤空气/流体/可替换植物/黑名单；上限 max_voxels（默认 512）；只有带 mv:connector 标签的方块允许跨接；带 mv:no_assemble 的方块视为边界。
2. VoxelSet → Blueprint：相对坐标 + BlockState + 密度 ρ + 功能标签；序列化 JSON 存 plugins/MoreVehicles/blueprints/，带版本号。
3. 质量属性：m = Σρ；质心 c = Σ m_i r_i / m；惯性张量 I = Σ m_i(|r_i|²E − r_i r_iᵀ) + 本体立方惯量，保留非对角项；参考点 = 质心。
4. 几何：S 取三轴投影面积最大者，b / cbar 取对应跨度，AR = b²/S；碰撞体 = 合并 OBB 集合。
5. 功能部件（按 blocks.yml 的 tag → Part）：
   mv:seat（主驾/乘客、视角偏移）、mv:thruster（轴向、T_max、能耗、矢量/差动）、mv:wing（S、CLα、α_stall、AR、安装角）、mv:control_surface（升降/副翼/方向、δ_max、响应速率）、mv:wheel / mv:bogie（接触点、μ、悬挂 k/c、r_w、J）、mv:connector（挂接点、F_hook_max）、mv:energy（容量/峰值功率）、mv:ballast（配重）。
   未打标签的普通方块只贡献质量、惯量、碰撞与外观——全石头搭也能装配成载具，只是没推力没舵。
6. 生成 VehicleRig 注册进 VehicleWorld，原方块按配置保留为碰撞代理或移除（默认移除，交给客户端渲染），广播 spawn。
7. 命令 /mv blueprint save <name> / spawn <name> [world x y z]；vehicle_configs/<name>.json 可覆盖任意气动/动力系数。

边界规则：无座椅不装配；超限/含禁配标签要给玩家明确原因且**不破坏原方块**；装配失败可回滚。

验收：
- 3×3×1 石头板 + 1 个驾驶座 → 成功装配，m / c / I 与解析值误差 < 1%。
- 加推进器 + 舵面后，Part 数量与方向正确；差动推力产生偏航力矩（与 P2 单测一致）。
- 蓝图 save → 重启服务端 → spawn，参数逐字段一致。
- vanilla 客户端能看到载具（Display 降级），Fabric 客户端看到高精度模型且不双重渲染。
```

---

## P7 · 气动/推进部件与控制映射

```text
【阶段 P7】目标：把 P6 的 Part 接到 P2 的 6DOF 内核上，实现"航空动力学级"可驾驶手感。
技术基线：MC 1.21.11 / 服务端权威物理 / 客户端只发归一化输入 / 通道 input 包 20 Hz。

实现：
- 每 tick：遍历 VehicleRig 的 Part，收集 ΣT（含 r×T 偏心/差动力矩）、ΣF_aero、ΣM_aero、接触力，交给 RigidBody6DOF.step(state, forces, dt)。
- 控制量归一化 [-1,1]：throttle(W/S)、pitch、yaw(A/D)、roll(Q/E)、brake(空格)、flap/gear(F/G)、矢量推力；Fabric InputSampler 每 tick 采样，带 seq 与 clientTime，服务端按 seq 去重/防重放。
- 舵面 δ 用一阶响应（响应速率可配）而不是瞬间到位；失速后舵效衰减；油门到推力可加 spool-up 惯性。
- 轮组/转向架在地面时接管接触力，离地后自动切纯气动；水/岩浆可选浮力与阻尼。

验收：
- 定油门直线加速：速度曲线与 ½ρV² 阻力平衡一致，存在稳定极速。
- α 超 α_stall 后 CL 下降、载具掉高度（失速可感知）。
- 左右推力差动 → 偏航角速度方向正确；升降舵 → 俯仰方向正确。
- 200 ms 模拟延迟下服务端状态连续、无跳变；客户端预测与服务端快照误差可收敛。
```

---

## P8 · Fabric 客户端渲染、预测、插值、HUD

```text
【阶段 P8】目标：Fabric 1.21.11 客户端完整表现。
技术基线：MC 1.21.11 / Fabric API 0.141.6+1.21.11 / environment: client / 服务端权威 / 通道 snapshot 10~20 Hz。

实现：
- 纯客户端实体：只在 ClientWorld 添加，不注册服务端 EntityType；收到 spawn 后隐藏服务端 Display 组避免双重模型。
- 1.21.11 渲染遵守 Extract/Draw 两阶段：几何/顶点写入在提取阶段完成，绘制阶段不读世界状态；自定义 RenderPipeline 只用于原版管线不够的场景。
- 预测：本地立即施加 input，缓存未确认输入队列，收到快照后回滚重放。
- 插值：100 ms 缓冲，位置 lerp、姿态四元数 slerp；误差 > 0.5 方块用 150 ms 指数平滑校正，禁止瞬移；脱轨事件允许一次硬同步并用粒子掩盖。
- HUD：空速/地速、α、β、油门、能量、L/V 风险条、编组节数与车钩受力、失速/脱轨预警。
- 键位、HUD、模型走资源包/配置，可关。

验收（给出实测截图或日志数值）：
- 人为注入 100 ms 延迟 + 5% 丢包，位置抖动 < 0.15 方块、无肉眼瞬移。
- 连续旋转 60 秒姿态无翻转/万向锁。
- 卸载 mod 后 vanilla 客户端仍能看到载具移动（P9 降级路径不回归）。
```

---

## P9 · 降级渲染、命令、性能与回归

```text
【阶段 P9】目标：全量收尾，未装 Fabric 的玩家也能看到并使用载具。
技术基线：MC 1.21.11 / Purpur 服务端（Paper Display API）/ Java 21 / 通道 morevehicles:v1。

实现：
- DisplayRig：用 BlockDisplay（方块外观）+ ItemDisplay（模型件）+ Interaction（可点击命中盒）按刚体变换拼装；用 Transformation / setTransformationMatrix(Matrix4f) 摆位，interpolationDuration / teleportDuration 做服务端平滑；按距离剔除、数量上限、同类合并。
- 未握手 vanilla 客户端：控制退化为原版事件（右键上车、潜行下车、方向键近似油门），不提供 HUD 与高精度物理，但必须能看到编组、动力矿车、脱轨翻滚。
- 命令全集：/mv version|couple|uncouple|rerail|dynacurve|reload|blueprint save|spawn|debug net；权限节点 mv.*、mv.admin；载具 owner/白名单；跨维度与区块卸载时持久化并恢复。
- 性能：物理每 tick 零垃圾分配；按区块空间索引载具；快照批量合并；Display 数量上限；重计算（曲率拟合/凸包）只在装配时做；广播回主线程。
- 写 docs/ProtocolSpec.md 完整版与 docs/TUNING.md（κ、Nadal、气动系数调参指南）。

验收：
- vanilla 客户端进服可见载具、可上下车、可见脱轨。
- 50 台载具 + 20 玩家，服务端稳定 20 TPS（给出 /tps 与 timings 摘要）。
- 全部 P0~P8 单测与集成测试一次通过（贴 TEST-MV 跑回归）。
- 热重载 /mv reload 后新系数立即生效，无需重启。
```

---

## FIX-1 · 当 AI 想用单端 / 混合端绕开约束

```text
停止并纠正方向。本项目硬约束是：Purpur 1.21.11 是**服务端** Bukkit/Paper 插件，Fabric 1.21.11 是**客户端** mod，双端都是 1.21.11、Java 21，两个独立工程。
不允许：把 Purpur 当 Fabric 端、建议"两端都装同一个 Fabric mod 就完事"、引入 Arclight/混合端、把物理放到客户端算。
请回到 docs/MV-SPEC.md 第 2 节，重述服务端权威 + 客户端表现 + PLAY 阶段协议协商 + Display 降级的四层职责，然后给出在不破坏该前提下的替代方案，再继续。
```

---

## FIX-2 · 当 S2C 包收不到（P1 不通）

```text
按以下顺序逐项排查并给出每项的实测结论，不要凭猜改代码：
1. 服务端是否收到 C2S hello？若没收到 → 查 Fabric 侧 ClientPlayNetworking.send 是否在 PLAY 阶段、payload Id 是否与 playC2S 注册一致。
2. 服务端是否把该玩家标记为 mvCapable 并显式注册出站通道？Bukkit 只向用 minecraft:register 声明过通道的客户端下发，Fabric 客户端默认不声明 → 必须靠 C2S hello 触发注册，并补发声明做双保险。
3. Fabric 侧是否把该 Id + StreamCodec 注册到 PayloadTypeRegistry.playS2C()？未注册会报"未注册 CLIENTBOUND PLAY 类型"或静默丢弃。
4. 若 1~3 都对但仍收不到 → 启用 fallback_mixin：Mixin 到 CustomPacketPayload.codec() 的 FallbackProvider，对 morevehicles:v1 返回自定义 CODEC（这是 Bukkit→Fabric 原始 payload 的已知变通）。
5. 是否在代理（Velocity/Bungee）后面？检查 bungeecord:main 是否改写通道。
6. 字节是否对齐？用 common 层对拍单测比对 VarInt、UTF-8 长度前缀、浮点顺序、枚举 ordinal。
每项给出日志片段或测试输出；全部通过前不要写业务包。
```

---

## FIX-3 · 当物理抖动 / 爆炸 / NaN

```text
按此清单逐项验证并给出数值：
1. dt 是否固定 0.05 且与服务端 TPS 解耦（不能拿帧时间当 dt）。
2. 四元数每步是否归一化；I 是否正定、求逆是否稳定；非对角项是否被错误丢弃。
3. 低速奇异：动力矿车 ηP/max(v,v_min) 的 v_min 是否生效；κ 在 v=0 是否有限。
4. 黏着/功率 min 是否在 v→0 时把 F_t 限到 μ m g；车钩弹簧刚度 k 与 dt 是否满足稳定条件（必要时降 k 或子步积分）。
5. 曲率 κ=1/R 在直轨是否除以 0；轨道曲率是否做了有限差分平滑。
6. 单位是否混用（1 方块 = 1 m、gravity_scale、km/h 与 m/s 的 Curtius–Kniffler 换算）。
7. 接触冲量是否在同一 tick 被重复施加；脱轨切换自由刚体时约束是否只解除一次。
修复后重跑 P2/P4/P5 的参数化单测，并给出修复前后对比数值。
```

---

## FIX-4 · 当客户端模型错位 / 双模型 / 闪烁

```text
排查顺序：
1. 坐标系：服务端刚体是体轴系 + 四元数，客户端渲染是否把四元数按同一手性/分量顺序 (x,y,z,w) 应用；Y-up 与体轴前方向是否一致。
2. 1.21.11 的 Extract/Draw 分离：几何写入是否在提取阶段完成，绘制阶段是否误读世界状态导致一帧延迟。
3. 收到 spawn 后是否隐藏了服务端 Display 组；未隐藏会出现双重模型/闪烁。
4. 插值缓冲是否 100 ms；slerp 是否走了最短弧（点积为负时取反）。
5. 参考点：服务端质心为原点，客户端模型 pivot 是否也是质心（BlockDisplay 默认角点、ItemDisplay 默认中心，别混用）。
6. 误差校正是否用了硬设位置；>0.5 方块应走 150 ms 平滑。
给出一张对照表：服务端 /mv debug 的 pos+quat、客户端渲染用的 pos+quat、差值。
```

---

## TEST-MV · 回归测试提示词

```text
对本仓库执行完整回归，贴出每一项的真实命令与输出摘要，不允许用"应该通过"代替：
1. ./gradlew build（三模块编译 + 全部 JUnit）
2. 物理内核：真空抛体、四元数 10 万步、惯性张量对称性、失速 CL 衰减、能量漂移
3. 协议对拍：每个 PacketType 的 common encode → server decode → client decode 逐字节一致
4. P1 连通性：hello / hello_ack / ping / pong 双向日志；vanilla 客户端不报错
5. 矿车：直轨/坡道/弯道/道岔；1/4/8 节编组极速单调；断钩分裂；/mv dynacurve 平衡点误差 <3%
6. 脱轨：大半径低速零脱轨、小半径高速必脱轨、同种子可复现、rerail 成功
7. 装配：石头板 + 驾驶座的 m/c/I 解析误差 <1%；蓝图 save→重启→spawn 一致
8. 气动：失速掉高度、差动推力偏航、舵面响应速率
9. 客户端：100 ms 延迟 5% 丢包下抖动 <0.15 方块、60 秒旋转无翻转
10. 降级与性能：vanilla 可见、50 载具 20 TPS、/mv reload 即时生效
输出一张结果表：项 / 通过或 BLOCKED / 关键数值 / 失败原因（若有）。任何 BLOCKED 都不要自行降级验收标准，先报给我。
```

---

## REVIEW-MV · 合并前审查提示词

```text
以"严格 reviewer"身份审查当前 diff，只报问题、不改代码，按严重度排序（BLOCKER / MAJOR / MINOR）：
- 是否违反 SYS-MV 硬约束（双端角色、服务端权威、PLAY 阶段、不碰原版实体类、common 零 MC 依赖）。
- 跨端字节是否仍与 docs/ProtocolSpec.md 单一来源一致；协议版本是否递增。
- 物理公式是否与 docs/MV-SPEC.md 第 4~5 节一致（κ、Davis、Nadal/Wagner、6DOF、气动导数），有无魔法数字、有无低速奇异、有无每 tick 分配。
- 配置是否可热重载、参数是否有来源说明（Curtius–Kniffler / Davis / Nadal / APTA / 6DOF）。
- 是否有未实测就写死的 API 名称；1.21.11 mapping 是否核对过。
- 降级路径（vanilla 客户端 / Display 实体）是否被回归覆盖。
- 安全：输入 seq 防重放、权限节点、装配上限与回滚、玩家不能通过蓝图越权生成实体。
每条给出 文件:行号 + 具体问题 + 建议修法。最后给一句明确结论：可合并 / 不可合并 + 剩余 BLOCKER 数量。
```

---

## 三域物理增补（空中 / 水面 / 水下）——优先级高于前文

> 执行顺序改为：P0 → P1 → P2 → **P2-F** → P3 → P4 → P5 → P6 → **P6-F** → P7 → **P7-F** → P8 → P9。
> 总纲对应 `docs/MV-SPEC.md` 第 11 节。以下所有约束与前文冲突时，以本节为准。

### SYS-MV-F · 追加全局约束（贴在 SYS-MV 之后）

```text
【三域物理硬约束，追加到前面的技术基线】
- 载具必须同时支持空中、水面、水下三种介质，且共用一套 6DOF 状态与积分器；差异只允许来自 FluidMedium.sample(worldPos,tick) 返回的 {介质类型, ρ, μ, u_flow, p_static, 饱和度 α}。
- 严禁把"是否在水里"写成整台载具的布尔开关；必须按每个 Part 变换后的世界坐标分部采样介质，各自算完力/力矩再求和。同一 tick 允许机翼在空气、船体在水、螺旋桨半浸没。
- 相对流速 v_rel = R(q)·v_b − u_flow；所有 qbar、Re、Fr、J、σ 都必须基于 v_rel（逆流掉速、顺风增速、气泡柱抬升自然出现）。
- 浸没比 α = smoothstep(d/ε)，ε≈0.25 方块；ρ_eff = α·ρ_w + (1−α)·ρ_air；C_D、C_a、舵效、推进效率、浮力全部按 α 连续插值，禁止阶跃。
- 浮力 F_b = ρ_w·g·∇，作用点在浮心 B，力矩 M_b = (B−G)×F_b；初稳性 BM = I_wp/∇，GM = KB+BM−KG，扶正力矩 = ρ_w·g·∇·GM·sinφ；GM<0 必须真的倾覆；压载舱半满要按 GM_corr = GM − ΣFSM/Δ 削弱稳性。
- 附加质量 m_a = C_a·ρ·∇ 以惯性项进入 (m+m_a)a 与 (I+I_a)ω_dot，匀速时为零；细长体轴向 C_a≈0.1、横向≈1.0，方向插值 C_a(θ)=0.1+0.9sin²θ。
- 密度表：ρ_w=1.0、ρ_stone=2.4、ρ_wood=0.6、ρ_iron=7.8、ρ_air=0.0012；ρ_air(h)=ρ_0·exp(−h/H)。木结构自然浮、石结构必须靠浮力舱或中空排水体积才能浮。
- 阻力 C_T = (1+k)C_F(Re) + C_form + C_W(Fr) + C_A；Fr = V/√(g·L_wl)，Fr≈0.30/0.50 有兴波阻力峰，≈0.40 是排水型上限，>0.7 进滑行态 C_W 骤降。
- 空化数 σ = (p_∞ − p_v)/(½ρV²)，p_∞ = p_0 + ρ_w·g·h；σ<σ_crit 时 CL 骤降、CD 上升、推力跌落并产生气泡事件；潜深增加可缓解空化。
- 水中螺旋桨 T = ρ_w·n²·D⁴·K_T(J)，J = V_A/(nD)，Q = ρ_w·n²·D⁵·K_Q，η0 = J·K_T/(2π·K_Q)；K_T 随 J 上升而下降，禁止用常量推力。喷气 mv:jet 入水失效，喷水 mv:waterjet 吸水口必须 α>0.8。
- 轻于空气的浮空器：F_net = (ρ_air(h) − ρ_gas)·g·V_gas − m·g，存在自然升限；气囊可压缩 V(h)=V0·p0/(p0+ρgh)。两类飞行体都走同一刚体。
- 入水/出水：α 跨 0/1 的帧自动切 4 子步；入水冲击 F_slam = ½ρ_w·A_wet·v_⊥²·C_s 作用 ≤2 tick 并钳制冲量；加隐式阻尼抑制水面 bobbing；力/力矩单步上限。
- 脱轨落水的矿车复用本流体求解器（浮力/附加质量/水阻自由刚体），水下轨道段按水阻修正 κ 函数。
- 严禁：把水下写成"空气阻力×常数"、把浮力写成固定向上加速度、忽略浮心与质心不重合、忽略附加质量、用速度阈值伪造空化/兴波。
- 所有新系数进 config.yml 且可 /mv reload 热重载；common 层仍零 MC 依赖，流体求解器必须可 JUnit 单测。
```

### CTX-MV-F · 追加复述要求

```text
除原有复述外，再用不超过 10 行复述第 11 节：分部介质采样、α 连续插值、浮力/浮心/GM 稳性公式、附加质量系数、Fr 与兴波阻力峰、σ 空化判据、K_T–J 螺旋桨、入水冲击与子步、装配时预计算的静水力曲线 LUT。若总纲第 4 节与第 11 节有冲突，以第 11 节为准并列出冲突项。
```

### P2-F · 介质场、浮力稳性与附加质量内核（插在 P2 之后）

```text
【阶段 P2-F】目标：在 common/ 建三域共用的流体内核，让"一块木头能浮、一块石头会沉、船会自己回正"。
技术基线：MC 1.21.11 / 服务端权威 / common 零 MC 依赖 / 6DOF 方程不变，只扩展介质与力汇。

实现：
1. FluidMedium / MediumField：sample(worldPos,tick) 返回 {类型, ρ, μ, u_flow, p_static, α}；空气用 ρ_air(h)=ρ_0·exp(−h/H)，水 ρ_w=1.0、岩浆 ρ≈2.7 且高黏度，气泡柱给向上 u_flow，waterlogged 方块不计入流体体积。自由面高度 y_s 由连续水方块顶面+水位插值得到。α=smoothstep(d/ε)，ε=0.25。
2. Hydrostatics：按部件 OBB/体素与水线裁剪算 V_sub(h)、浮心 B(h)、A_wp(h)、I_wp(h)、GZ(φ) 曲线；F_b=ρ_w·g·∇ 作用于 B，M_b=(B−G)×F_b；GM=KB+BM−KG，扶正力矩=ρ_w·g·∇·GM·sinφ（小角），大角查 GZ；GM<0 倾覆；自由液面修正 GM_corr=GM−ΣFSM/Δ。
3. AddedMass：m_a=C_a·ρ·∇、I_a=C_a^rot·ρ·∇·L²；细长体轴向 0.1 / 横向 1.0，C_a(θ)=0.1+0.9sin²θ；有效质量 (m+α·m_a) 与 (I+α·I_a) 进入 6DOF，匀速不产生力。
4. WaveResistance：C_T=(1+k)C_F(Re)+C_form+C_W(Fr)+C_A；Fr=V/√(g·L_wl)；C_W 用含 Fn≈0.30 与 0.50 峰、≈0.40 后陡升、>0.7 骤降的可配曲线（查表插值）；完全浸没时 C_W→0。
5. Cavitation：σ=(p_∞−p_v)/(½ρV²)，p_∞=p_0+ρ_w·g·h；σ<σ_crit 时 CL 衰减因子、CD 增量、推力惩罚连续可微，禁止 if-else 硬切。
6. PropellerCurve：T=ρ·n²·D⁴·K_T(J)，J=V_A/(nD)，K_T≈K_T0−k·J（Wageningen B 简化），Q=ρ·n²·D⁵·K_Q，η0=J·K_T/(2π·K_Q)；介质白名单（jet 仅空气、propeller 仅水、amphibious 双曲线按 α 插值）。
7. TransitionSolver：分部采样 → 按 α 插值所有介质量 → 入水冲击 F_slam=½ρ_w·A_wet·v_⊥²·C_s（≤2 tick、冲量钳制）→ α 跨 0/1 帧切 4 子步 → 隐式阻尼抑制 bobbing → 单步力/力矩钳制。

验收（JUnit，给出实际数值）：
1. 静水木块：稳态 ρ_w·g·∇=mg，吃水误差 <2%；石块同条件沉底。
2. GM>0 施加 15° 横倾后回正且衰减；GM<0 倾覆；半舱压载使 GM_corr 低于 GM。
3. 同推力下水下加速度 ≈ 空气值 /(1+C_a·ρ·∇/m)，误差 <5%；匀速时附加质量不产生额外力。
4. Fr 扫描 0.1~1.0：C_W 在 0.30/0.50 附近出现峰、0.40 后陡升、>0.7 下降。
5. σ 从 3 降到 0.2：CL 单调下降；把 h 增大 10 方块后同速度 σ 回升、CL 恢复。
6. 100 次入水/出水（含高速垂直入水）：无 NaN/Infinity、力跳变不超过钳制值、角速度连续。
7. 半浸没场景：上部部件取空气、下部取水的受力之和 = 手工分部计算值，误差 <1%。
禁止：此阶段不写渲染、不写命令、不碰 Bukkit/Fabric 类型。
```

### P6-F · 装配时的静水力与部件标签（插在 P6 之后）

```text
【阶段 P6-F】目标：让"驾驶座一放"自动算出浮心、稳性高度与水线，并识别水/空两套部件。
技术基线：MC 1.21.11 / Purpur 服务端装配 / P2-F 的 Hydrostatics 复用 / 装配期一次性计算。

实现：
1. 在 P6 的 assemble() 派生参数里追加并缓存：∇(h)、B(h)、A_wp(h)、I_wp(h)、GZ(φ)、GM、L_wl、S_wet、三轴 C_a 与 I_a、压载舱 FSM、吃水线 LUT（按高度 0.05 方块步进）。运行期只查表插值，禁止每 tick 重算凸包/裁剪。
2. 装配时按世界方块判定介质：识别水/岩浆/气泡柱/水流向量/水位高度；waterlogged 只算碰撞代理。
3. 新增标签写入 blocks.yml：
   mv:buoyancy（V0、可压缩性、气体密度）、mv:ballast_tank（容量、注排速率、FSM）、mv:hydrofoil（S、CLα、σ_crit、安装角）、mv:rudder（δ_max、舵效、浸没要求）、mv:propeller（D、P/D、K_T(J)/K_Q(J) 曲线、介质白名单）、mv:jet（仅空气）、mv:waterjet（吸水口 α>0.8）、mv:keel（横摇阻尼/侧滑阻力）、mv:sail（帆面积/风向响应）。
4. 普通方块继续贡献质量、惯量、排水体积与湿表面积：全石头结构会沉；做成中空船型或加 mv:buoyancy 才浮。装配面板新增显示：排量 / 吃水 / GM / 浮心偏移 / 介质。
5. 蓝图 JSON 增加 hydrostatic 段与部件介质白名单；save→重启→spawn 后静水力曲线逐字段一致。
6. 压载控制：/mv ballast <rig> <fill|drain|set %>，改变 m 与 G；GM_corr 实时更新；注排可逆。

验收：
- 3×3×1 木板 + 座椅：GM>0、能浮；同形状石头板：沉底；解析 V_sub 与 LUT 误差 <1%。
- 加 mv:buoyancy 后石头板也能浮；加半舱压载 GM_corr 下降且横摇变慢。
- 蓝图重启后 ∇(h)/B(h)/GM 逐字段一致。
- 装配 512 体素上限时静水力预计算耗时 < 50 ms（给出实测），运行期每 tick 零额外分配。
```

### P7-F · 水动力、介质相关推进与跨域过渡（插在 P7 之后）

```text
【阶段 P7-F】目标：把 P2-F 的流体求解器接到真实载具上，实现"能飞、能漂、能潜、能入水"。
技术基线：MC 1.21.11 / 服务端权威 / 通道 input 20 Hz / 6DOF 同一套积分器。

实现：
1. 每 tick 对每个 Part 用其世界坐标 sample 介质 → 分部计算气动力/水动力、浮力、附加质量、推力、舵效 → ΣF/ΣM → RigidBody6DOF.step(state, forces, dt)。严禁整体 isInWater。
2. 空气域：沿用 P7 的 CL/CD/CY 与力矩导数；补 ρ_air(h) 随高度衰减、地效（离地<b/2 时 C_Di 乘 <1 系数）、确定性阵风/风切变、失速后尾旋开关。
3. 浮空器：F_net=(ρ_air(h)−ρ_gas)gV_gas−mg，气囊 V(h)=V0·p0/(p0+ρgh)，升限自然出现；CL≈0，附加质量按 ρ_air 计。
4. 水面/水下：浮力+扶正力矩+兴波阻力+滑行态切换（Fr>0.7 水动升力接管）；水翼/水舵用同一导数公式但 ρ=ρ_w；空化触发时 CL 衰减、推力跌落并广播 cavitation 事件。
5. 推进：mv:jet 入水失效；mv:propeller/ waterjet 用 K_T(J) 曲线，J=V_A/(nD)；两栖按 α 插值；洋流/风/气泡柱进入 v_rel；差动推力在水下产生偏航但受附加质量阻尼。
6. 跨域：α 平滑插值全部介质量；入水冲击冲量钳制；α 跨 0/1 帧 4 子步；水面振荡用隐式阻尼；入水/出水/倾覆/空化/地效/压载变化广播 event。
7. 脱轨矿车落水（P5）复用本求解器，水下轨道段按水阻修正 κ；/mv rerail 可从水下恢复。
8. 控制映射扩展：压载 fill/drain、浮力舱放气、水翼攻角、螺旋桨转速 n、帆角；键位与 HUD 可配。

验收（给出实际曲线/数值）：
- 同一台两栖载具从空中俯冲入水→水下巡航→跃出水面→爬升，全程无 NaN、姿态连续、无瞬移；α 曲线平滑。
- 水面定油门：速度接近 Fr≈0.40 时功率代价陡升；加水翼后 Fr>0.7 抬出水面、阻力骤降。
- 深潜 10 方块后同速度 σ 回升、空化消失、推力恢复。
- 螺旋桨 n 固定时，随航速上升 J 增大、K_T 下降、推力单调衰减；η0 与曲线自洽。
- 木船半舱压载横摇周期变长；GM<0 时受扰倾覆且事件已广播。
- 逆洋流/顺风速差与 v_rel 计算一致。
禁止：不要用"入水就把重力改小"这类假物理；不要用速度阈值伪造空化或兴波峰。
```

### FIX-5 · 入水爆炸 / 浮力振荡 / 水面抖动 / 空化不触发

```text
按此顺序逐项给出实测数值，不要凭猜改代码：
1. 是否用了整台载具的布尔 isInWater？必须改为按 Part 分部采样 + α 连续插值；阶跃力是最常见爆炸源。
2. 浮力是否与重力在同一 tick 相互过冲？加隐式阻尼/半隐式求解，检查 ρ_w·g·∇ 与 mg 的稳态差。
3. 入水冲击是否被钳制？F_slam 作用 ≤2 tick、冲量上限、α 跨 0/1 帧是否切了 4 子步。
4. 浮心 B 与质心 G 是否都被正确变换到世界系；M_b=(B−G)×F_b 的力臂是否用了质心为原点；GM 是否近 0（需横摇阻尼 C_roll）。
5. 附加质量是否只在加速度项生效、匀速时是否误当阻力重复施加。
6. 单位是否混用：ρ_w=1.0 / ρ_air=0.0012 / g=gravity_scale / Fr 用 m·s⁻¹ / K_T 的 n 用 rev/s / σ 的 p_∞ 是否含 ρgh。
7. 空化不触发：确认 p_∞=p_0+ρ_w·g·h 而不是常数 p_0；σ 用的是 v_rel 还是地速。
8. 水面高度 y_s 是否在方块边界跳变（必须用水位插值）；ε=0.25 是否被误设成 0。
修复后重跑 P2-F、P7-F 全部验收项，给出修复前后对比数值。
```

### TEST-MV-F · 回归增补（追加到 TEST-MV 第 10 项之后）

```text
11. 静水：木块 ρ_w·g·∇=mg 吃水误差<2%；石块沉底；GM>0 回正 / GM<0 倾覆 / 半舱 GM_corr 下降
12. 附加质量：水下加速度 ≈ 空气 /(1+C_a·ρ·∇/m) 误差<5%；匀速时无额外力
13. 兴波：Fr 扫描 0.1~1.0，0.30/0.50 峰、0.40 陡升、>0.7 下降可复现
14. 空化：σ 下降 CL 单调衰减；深潜 10 方块后恢复；推力惩罚生效
15. 螺旋桨：J 上升 K_T 下降、η0=J·K_T/(2π·K_Q) 自洽；jet 入水失效、waterjet 出水失推
16. 跨域：100 次入水/出水无 NaN、力跳变在钳制内、α 与角速度连续；半浸没分部受力误差<1%
17. 浮空器：净浮力随高度衰减、有升限；气囊放气可控下降
18. 两栖全流程：空中俯冲→入水→水下巡航→跃出→爬升，服务端状态连续、vanilla Display 与 Fabric 模型同步
19. 压载：注水下潜/抽水上浮可逆，深度与质量单调；脱轨落水可 rerail
输出同样并入结果表；任何 BLOCKED 先报给我，不许降级标准。
```

### REVIEW-MV-F · 审查增补（追加到 REVIEW-MV 检查项）

```text
- 是否违反 SYS-MV-F：整台载具布尔介质、水下=空气阻力×常数、浮力写成固定加速度、忽略浮心/附加质量/空化/兴波、用阈值伪造物理。
- 分部采样是否真的按 Part 世界坐标；α 是否 smoothstep 连续；ρ_eff/C_D/C_a/舵效/推进效率是否全部插值。
- 公式是否与 MV-SPEC 第 11 节一致（F_b、M_b、BM=I_wp/∇、GM、GM_corr、m_a、C_T/Fr、σ 含 ρgh、K_T/J/η0、F_slam、V(h) 气囊）；参数是否有来源说明。
- 静水力曲线是否在装配期预计算并 LUT 化，运行期是否零重算、零每 tick 分配。
- 入水帧子步、冲量钳制、隐式阻尼是否覆盖；水面高度是否插值而非方块跳变。
- snapshot/event 新字段是否与 ProtocolSpec.md 一致、协议版本是否递增；HUD 三套仪表与降级粒子是否齐全。
- 新增密度表是否与方块密度表一致（木浮石沉），压载 FSM 与 GM_corr 是否联动。
每条给出 文件:行号 + 问题 + 修法；结论必须包含三域验收是否全部通过。
```
