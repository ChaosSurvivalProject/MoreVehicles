# 《我的世界》更多载具（More Vehicles）· 双端技术方案

> 配套文件：`我的世界更多载具_AI提示词包.md`（可直接复制给 AI 编程助手执行）
> 定位：本文是**给 AI 的工程输入总纲**，定义架构、公式、协议、数据结构与验收口径；不写具体实现类代码，代码由提示词包分阶段驱动生成。

---

## 0. 版本基线核验

按你的更正，**双端统一为 Minecraft Java 1.21.11，Java 21**。需要先把一处角色说清楚：**Purpur 是服务端核心（Bukkit/Paper 插件生态），Fabric 是客户端模组生态**，所以"双端配合"= 服务端 Purpur 插件 + 客户端 Fabric Mod，两个独立工程，靠自定义网络协议协同。

| 项 | 结论 |
|---|---|
| 游戏版本 | 1.21.11（2025-12-09 发布），服务端与客户端均要求 Java 21+ |
| 服务端 | Purpur 1.21.11（最新稳定 #2568），依赖 `purpur-api:1.21.11-R0.1-SNAPSHOT`，含 Paper/Bukkit/Spigot 全部 API |
| 客户端 | Fabric 1.21.11，Loader 0.20.1 / Fabric API 0.141.6+1.21.11；`fabric.mod.json` 中 `minecraft: ~1.21.11`、`java: >=21`、`environment: client` |
| 网络 | 1.20.5+ 的自定义 payload 必须在**两端**用 `PayloadTypeRegistry.playC2S()/playS2C()` 注册 `CustomPacketPayload.Id` + `StreamCodec`，再注册 receiver |
| 降级渲染 | Paper 自 1.19.4 起提供 `BlockDisplay / ItemDisplay / TextDisplay`，支持任意仿射变换与服务端插值 |
| 渲染 | 1.21.11 采用"提取(Extract)/绘制(Draw)"两阶段管线，自定义渲染必须遵守阶段分离 |

**硬约束（写进所有提示词）**：Purpur 不能加载 Fabric Mod，Fabric 客户端不能承载权威逻辑；物理只在服务端算，客户端只做输入采集、预测、插值、渲染。

---

## 1. 目标与边界

### 1.1 交付功能

1. **矿车可相互连接**：相邻矿车自动/手动连挂成编组，车钩有弹性、有强度上限、可断钩。
2. **动力矿车**：新增可控动力车，带功率、能耗、黏着限制、恒功率/恒扭矩分段特性。
3. **优化矿车动力系数函数**：把原版"常量加速 + 常量摩擦"替换为关于速度、坡度、曲率、载荷、黏着、功率、编组长度的可微函数，配置化、可热重载、无低速奇异。
4. **矿车可能脱轨**：基于 Nadal / Wagner L/V 判据 + 欠超高 + 蛇行 + 断钩/撞击/轨道缺失，支持概率脱轨与硬核确定脱轨。
5. **载具真实物理化（三域统一）**：6 自由度刚体、四元数姿态、惯性张量、动态压力、升力/阻力/侧力、三轴气动力矩与阻尼导数、推力矢量、失速；**空中 / 水面 / 水下共用同一套方程**，只替换介质参数与力汇（见第 11 节）。
6. **自定义载具**：**只需把一个「驾驶座」放到对应方块上**，右键即可把该方块连通结构整体识别为一台载具；自动推算质量、质心、惯性张量、迎风面积、翼展、浮心、稳性高度、部件功能，生成可驾驶刚体。
7. **天上飞的**：重于空气（机翼/旋翼/喷气/矢量推力，含失速、风场、密度随高度、地效）与轻于空气（浮空器：净浮力、升限、气囊可压缩）两类，都由同一刚体求解。
8. **水里的**：水面（浮力/浮心/初稳性 GM/兴波阻力/滑行态）、水下（附加质量、压载下潜、水压、水翼空化、螺旋桨 K_T–J 曲线）、以及**入水/出水跨域过渡**（分部采样介质 + 连续插值 + 入水冲击），不是"加个浮力"了事。

### 1.2 非目标

- 不修改原版 `Minecart` 实体类，不改原版物理引擎；载具是**新增的平行系统**。
- 不做服务端 Mod（不引 Arclight / 混合端）：保持 Purpur 纯净插件部署。
- 不强制所有玩家装 Fabric Mod：未装 Mod 的客户端必须能"看到并基本可用"。

### 1.3 一句话架构原则

> **服务端权威物理 + 客户端表现增强 + PLAY 阶段自定义协议协商 + 原版 Display 实体降级兜底。**

---

## 2. 双端架构

```
┌──────────────────────── 服务端：Purpur 1.21.11 / Java 21 ────────────────────────┐
│  MoreVehicles-Server（paper-plugin.yml，Bukkit/Paper 插件）                       │
│                                                                                  │
│  VehicleWorld   ← 20 TPS 固定步长 6DOF 积分（权威）                              │
│   ├─ RailGraph        轨道图：切线 / 曲率 κ / 坡度 θ / 超高 e / 道岔              │
│   ├─ TrainSet         编组：Car + Coupling（弹簧-阻尼 + 车钩强度）                 │
│   ├─ VehicleRig       自定义载具刚体：Blueprint + List<Part>                     │
│   ├─ Blueprinter      驾驶座 → BFS 连通体 → 质量/惯量/几何/功能部件               │
│   ├─ AeroSolver       动态压力、CL/CD/CY、Cl/Cm/Cn、阻尼导数                     │
│   ├─ DerailmentModel  Nadal / Wagner、欠超高、风险概率、脱轨后自由刚体              │
│   ├─ SnapshotBus      按区块订阅，10~20 Hz 批量快照                              │
│   ├─ DisplayRig       BlockDisplay / ItemDisplay 拼装（vanilla 客户端可见）        │
│   └─ MVProtocol       Bukkit Messenger 读写 morevehicles:v1 字节                  │
└──────────────────────────────────▲──────────────────────────────────────────────┘
                                   │ PLAY 阶段 Custom Payload
                                   │ morevehicles:v1（C2S / S2C）
┌──────────────────────────────────▼──────────────────────────────────────────────┐
│  客户端：Fabric 1.21.11 / Java 21（environment: client）                          │
│  MoreVehicles-Client                                                             │
│   ├─ Handshake            能力协商 + 通道声明                                     │
│   ├─ InputSampler         WASD / 鼠标 / 按键 → 归一化控制量，20 Hz                │
│   ├─ Predictor           本地立即施加输入 + 未确认输入重放                         │
│   ├─ SnapshotBuffer      100 ms 缓冲，lerp + 四元数 slerp                        │
│   ├─ ClientVehicleEntity 纯客户端实体（不注册服务端注册表）                        │
│   ├─ VehicleRenderer     体素/Blockbench 模型 + 自定义 RenderPipeline             │
│   └─ HUD                 速度 / 攻角 / 推力 / 能量 / 脱轨风险                     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

| 层 | 权威方 | 职责 |
|---|---|---|
| 物理 | Purpur 服务端 | 状态积分、碰撞、脱轨判定、编组力、蓝图装配、持久化 |
| 表现 | Fabric 客户端 | 输入、预测、插值、模型渲染、HUD、音效粒子 |
| 兼容 | Purpur 服务端 | Display 实体近似外观 + 原版事件映射到简化控制 |

---

## 3. 协议层（最容易翻车，单独成节）

### 3.1 通道与注册

- 命名空间：`morevehicles:v1`，全部在 **PLAY 阶段**。
- Fabric 侧：`PayloadTypeRegistry.playC2S()/playS2C().register(ID, CODEC)` → `ClientPlayNetworking.registerGlobalReceiver(ID, handler)` → `ClientPlayNetworking.send(payload)`。
- Purpur 侧：`getServer().getMessenger().registerOutgoingPluginChannel(plugin, "morevehicles:v1")` / `registerIncomingPluginChannel(...)`，用 `Player#sendPluginMessage` 写**与 Fabric `StreamCodec` 完全一致的裸字节**。
- Payload 定义统一为 `record ... implements CustomPacketPayload`，`Id` 用 `Identifier.of("morevehicles","v1")`，`CODEC` 用 `StreamCodec<RegistryFriendlyByteBuf, ...>`；服务端手写字节时按同一顺序编码。

### 3.2 四个已知坑与解法（必须让 AI 显式处理）

1. **Bukkit 只把消息发给"声明过该通道"的客户端**，而现代 Fabric 客户端不发 `minecraft:register` → S2C 静默丢弃、无日志。
   解法：Fabric 客户端 JOIN 后**先主动发 C2S `hello`**（C2S 一定可达），服务端收到后把该玩家加入 `mvCapable` 集合并显式注册通道，之后才下发 S2C；同时在 `minecraft:register` 通道补声明做双保险。
2. **Fabric 客户端的 `CustomPacketPayload.codec()` 不认识 Bukkit 原始通道**时会丢弃/报"未注册 CLIENTBOUND PLAY 类型"。
   解法：Fabric 侧把每个 MV payload 的 `Id + CODEC` 完整注册到 `playS2C()`；若仍被原版 fallback 拦截，追加一个 Mixin 注入 `FallbackProvider`，对 `morevehicles:v1` 返回自定义 `CODEC`（这是 Fabric 社区针对 Spigot→Fabric 的已知变通方案）。
3. **配置阶段（CONFIGURATION）Bukkit 无 plugin message API** → 握手、能力协商、蓝图下发一律放 PLAY 阶段，不设计配置阶段包。
4. **服务端无法向客户端注册自定义实体类型** → 服务端不生成自定义实体；用 Display 实体做近似，Fabric 客户端用纯客户端实体做高精度渲染，并在收到 `spawn` 后隐藏同名 Display 组。

### 3.3 Payload 清单

| 包 | 方向 / 频率 | 关键字段 |
|---|---|---|
| `hello` | C2S / 登录一次 | 协议版本、能力位（高精度渲染/预测/资源包）、客户端渲染尺度 |
| `hello_ack` | S2C / 一次 | 服务端版本、TPS、`gravity_scale`、物理参数摘要、是否启用增强模式 |
| `input` | C2S / 20 Hz | vehicleId、seq、throttle、pitch、yaw、roll、brake、flags、clientTime |
| `snapshot` | S2C / 10~20 Hz | 批量：vehicleId、tick、pos、quat、vel、omega、partBits、derailRisk、energy |
| `spawn` / `despawn` | S2C / 事件 | blueprintId 或体素清单 hash、初始变换、Display 组 id |
| `event` | S2C / 事件 | 脱轨、断钩、上轨、座位变更、爆炸（驱动音效/粒子） |
| `blueprint` | 双向 / 低频 | 蓝图 JSON 或 hash + 分块传输 |

### 3.4 字节布局约定

```
VarInt  packetType
int     vehicleId / seq
double  x, y, z            // 坐标
float   qx,qy,qz,qw        // 姿态，服务端保证已归一化
float   vx,vy,vz, wx,wy,wz
float   throttle/energy/derailRisk
VarInt  flags / partBits
VarInt  count → 变长数组
String  = VarInt 长度 + UTF-8 字节（与 PacketByteBuf#writeString 一致）
```

**跨端对齐铁律**：VarInt 编码、字符串长度前缀、浮点字节序、数组顺序、枚举 ordinal 全部由一份 `ProtocolSpec.md` 单一来源定义；任何改动必须同改两端并递增协议版本，否则拒绝握手。

### 3.5 同步策略

- 服务端权威，20 TPS 固定步长 `dt = 0.05s`。
- 客户端预测：本地立即施加 `input`，缓存未确认输入队列；收到快照后回滚重放。
- 快照插值：100 ms 缓冲，位置 `lerp`、姿态 `slerp`；误差 > `0.5` 方块用 150 ms 平滑校正，禁止瞬移。
- 广播裁剪：按载具所在区块 + 视距订阅，只对附近 `mvCapable` 玩家发高精度快照；vanilla 客户端走 Display 实体自带的服务端插值（`interpolationDuration` / `teleportDuration`）。

---

## 4. 物理内核（纯 Java，可脱离 MC 单测）

### 4.1 状态与 6DOF 方程

状态 $x = [\mathbf{r}, \mathbf{v}, q, \boldsymbol{\omega}, E]$，姿态用单位四元数（避免万向锁），体轴系：

$$
\dot{\mathbf r}=R(q)\mathbf v_b,\qquad
m\dot{\mathbf v}_b=\mathbf F-\boldsymbol\omega\times(m\mathbf v_b)
$$

$$
\mathbf I\dot{\boldsymbol\omega}=\mathbf M-\boldsymbol\omega\times(\mathbf I\boldsymbol\omega),\qquad
\dot q=\tfrac12\,q\otimes(0,\boldsymbol\omega)
$$

积分：半隐式欧拉（默认）或 RK4（硬核模式）；每步归一化 $q$；惯性张量 $\mathbf I$ 由蓝图体素装配得到，非对角项保留（非对称载具会明显表现）。

### 4.2 空中：气动力（航空动力学类比）

> 三域（空中 / 水面 / 水下）共用本节同一套 6DOF 状态与积分器，仅由 `FluidMedium` 替换介质参数与力汇；水面、水下、跨域过渡的完整模型见**第 11 节**。

动态压力 $\bar q=\tfrac12\rho V^2$，参考面积 $S$、翼展 $b$、平均气动弦 $\bar c$、展弦比 $AR=b^2/S$。

$$
C_L=C_{L0}+C_{L\alpha}\alpha+C_{Lq}\frac{qc}{2V}+C_{L\delta_e}\delta_e,\quad
|\alpha|>\alpha_{stall}\ \Rightarrow\ C_L\ \text{衰减（失速）}
$$

$$
C_D=C_{D0}+\frac{C_L^2}{\pi AR e}+k_\delta|\delta|,\qquad
C_Y=C_{Y\beta}\beta+C_{Y\delta_r}\delta_r
$$

$$
C_l=C_{l\beta}\beta+C_{lp}\hat p+C_{l\delta_a}\delta_a,\;
C_m=C_{m0}+C_{m\alpha}\alpha+C_{mq}\hat q+C_{m\delta_e}\delta_e,\;
C_n=C_{n\beta}\beta+C_{nr}\hat r+C_{n\delta_r}\delta_r
$$

其中 $\hat p=pb/2V,\ \hat q=q_c\bar c/2V,\ \hat r=rb/2V$。

$$
\mathbf F_{aero}=\bar q S[-C_D,\ C_Y,\ -C_L]^T,\qquad
\mathbf M_{aero}=\bar q S[bC_l,\ \bar c C_m,\ bC_n]^T
$$

叠加：$\mathbf F = m\mathbf g + \sum \mathbf T_i + \mathbf F_{aero} + \mathbf F_{contact} + \mathbf F_{buoy}$；力矩同理含推力偏心 $\mathbf r_i\times\mathbf T_i$（差动推力可产生偏航，这是"真实物理化"的关键手感）。

- 攻角 $\alpha=\arctan2(-v_z,v_x)$（体轴）、侧滑角 $\beta=\arcsin(v_y/V)$，均用**相对气流速度**（扣掉风场）。
- 所有系数来自 `vehicle_configs/*.json`，支持按部件覆盖；无翼载具自动退化为纯阻力 + 推力模型。

### 4.3 接触与碰撞

- 轨道/地面：法向约束 + 库仑摩擦（$\mu_s,\mu_k$）+ 悬挂弹簧阻尼 $k,c$。
- 方块碰撞：OBB 集合 vs 体素世界，冲量法求解，带恢复系数与摩擦；高速用连续检测（sweep）防穿墙。
- 水/岩浆：可选浮力与附加阻尼。

### 4.4 单位与比例尺

- 1 方块 = 1 m；质量单位 = "方块质量单位"（由方块密度表给出，默认石 = 1.0）。
- `gravity_scale` 可配：默认贴近原版手感（约 20 m/s²），硬核模式 9.81 m/s²；空气密度 $\rho$、维度系数可配。
- **所有公式写成纯函数** `AeroSolver / Coupling / DerailmentModel`，输入 `VehicleState + Config + dt`，输出 `Forces`，不引用 Bukkit/Fabric 类型，便于 JUnit 参数化测试。

### 4.5 从蓝图自动推导参数

$m=\sum \rho_b$，$ \mathbf c=\sum m_i\mathbf r_i/m$，$\mathbf I=\sum m_i(|\mathbf r_i|^2\mathbf E-\mathbf r_i\mathbf r_i^T)+\mathbf I^{box}_i$；
$S$ 取三轴投影面积最大者，$b$ / $\bar c$ 取对应跨度，参考点取质心。玩家可在配置里覆盖任意一项。

---

## 5. 矿车系统

### 5.1 轨道图 RailGraph

把铁轨方块解析为节点/边：位置、切线 $\mathbf t$、曲率 $\kappa=1/R$、坡度 $\theta$、超高 $e$（用相邻轨高度差与弯道方向推算）、道岔状态、供电/信号标记。弯道与坡度用有限差分平滑，避免曲率跳变导致数值爆炸。

### 5.2 编组与车钩

每节 `Car` 有弧长坐标 $s_i$、$v_i$、质量 $m_i$、Davis 系数 $(A_i,B_i,C_i)$。相邻车钩：

$$
F_c = k\big((s_{i+1}-s_i)-L_0\big)+c\,(v_{i+1}-v_i),\qquad |F_c|\le F_{hook}^{max}
$$

超限 → 断钩，编组分裂为两列并广播 `event`。支持靠近自动连挂、`/mv couple`、`/mv uncouple`、潜行右键解挂；车钩力沿链逐节传递（从动力车向两端累加），不是整车平均。

### 5.3 动力矿车与「优化后的动力系数函数」

**能量**：燃料 / 红石 / 电池，额定功率 $P$、效率 $\eta$、容量 $E$。

**牵引力（三段，低速无奇异）**：

$$
F_t(v)=\min\Big(\underbrace{\eta P/\max(v,v_{min})}_{\text{恒功率}},\ \underbrace{\mu(v)\,m_{drive}\,g}_{\text{黏着上限}}\Big)\cdot\Big(1-(v/v_{max})^{n}\Big)_+
$$

黏着系数用 Curtius–Kniffler 经验式（干轨 $\mu=0.161+\frac{7.5}{V+44}$，湿轨 $\mu=0.130+\frac{7.5}{V+44}$，$V$ 为 km/h），并按配置映射雨天/下界/灵魂沙等轨道状态。

**阻力（Davis 方程，逐节计算）**：

$$
R_i(v)=A_i+B_i v+C_i v^2
$$

$A$ 机械滚阻、$B$ 粘滞、$C$ 气动（含编组屏蔽系数：头车 $C$ 大、中间车 $C$ 小）。

**坡度与曲线阻力**：$m_i g\sin\theta$、$m_i g\,k_{curve}/R$。

**优化后的动力系数函数（本作核心，替代原版常量模型）**：

$$
\kappa(v,\theta,R,\mathbf m,\mathbf F_t)=
\frac{\sum_j F_{t,j}-\sum_i R_i(v)-g\sin\theta\sum_i m_i-g\,k_{curve}R^{-1}\sum_i m_i-\sum_i F_{c,i}^{\parallel}}
{g\sum_i m_i^{eff}}
$$

$$
m_i^{eff}=m_i+\frac{J_i}{r_w^2},\qquad
a_{train}=\frac{\kappa\,g}{1+\lambda(N-1)}
$$

相比原版的**六项优化**：

1. 由常量加速度 → 关于 $(v,\theta,R,\text{载荷},N,\text{黏着},P)$ 的**可微函数**，可作图、可求平衡点 $v^*$（令 $\kappa=0$）。
2. 低速用 $v_{min}$ 平滑，消除 $P/v$ 在 $v\to0$ 的奇异；高速用软限幅而非硬截断。
3. 阻力**逐节**算，编组长度与屏蔽效应真实影响极速。
4. 转动惯量折算进 $m^{eff}$，起步/制动更沉。
5. 黏着上限与功率上限取 min，动力车在湿轨会打滑（表现为 $\kappa$ 骤降 + 粒子/音效）。
6. 全部系数进 `config.yml`，支持 `/mv reload` 热重载 + 曲线查表（LUT）加速；提供 `/mv dynacurve` 输出 $\kappa$–$v$ 表便于调参。

### 5.4 脱轨模型

**主判据（Nadal）**：

$$
\left(\frac LV\right)_{lim}=\frac{\tan\delta-\mu}{1+\mu\tan\delta},\qquad
\frac LV=\frac{m v^2/R\cdot\cos\theta - m g\sin e + F_{c,\perp}+Y_{aero}}{m g\cos\theta \pm \Delta V}
$$

$\delta$ 轮缘接触角、$\mu$ 轮轨摩擦、$\Delta V$ 轮重减载；典型安全上限取 $0.8$（工程上常按 $L/V\le0.5$ 保守设计）。蛇行/冲角 $\beta$ 场景用 Wagner 变体把 $\cos\beta$ 引入分母。

**欠超高**：均衡超高 $C_{eq}=\dfrac{G V^2}{127R}$（mm，$V$ km/h，$R$ m），欠超高 $C_d=C_{eq}-C_{actual}$ 超限时抬升横向力。

**其它触发**：断钩后失控、轨道缺失/道岔冲突、撞击、超速、侧向气动突风、玩家强行切弯。

**概率化（"有可能脱轨"而非必然）**：

$$
r=\frac{(L/V)}{(L/V)_{lim}},\qquad
P_{derail}=\text{clamp}\big(\text{smoothstep}(r-1)\big)\cdot(1+\gamma\,C_d^{+})
$$

配置提供三档：`off` / `probabilistic`（默认，$r<1$ 永不脱轨）/ `hardcore`（$r\ge1$ 必脱轨）。每 tick 用服务端确定性随机（种子 = 载具 id + tick）判定，保证可复现。

**脱轨之后**：解除轨道约束 → 切为自由 6DOF 刚体（翻滚、掉落、碰撞），广播 `event`；可用 `/mv rerail` 或把车推回轨道重新上轨；脱轨瞬间按速度生成掉落物/破坏轨道的概率。

### 5.5 交互与命令

`/mv couple|uncouple|rerail|dynacurve|reload|blueprint save|spawn|debug`，权限节点 `mv.*`；`mv.admin` 可改物理参数；载具 owner / 白名单控制驾驶权。

---

## 6. 自定义载具：一个驾驶座 = 一整台载具

### 6.1 交互流程

1. 玩家把「驾驶座」方块放在任意方块结构上（或直接放在方块顶面）。
2. 右键座椅 → 触发 `AssembleEvent`，服务端以座椅下方/所在方块为根扫描。
3. 扫描成功后弹出提示：`已装配：质量 12.4 / 部件 7 / 推力 3 / 升力面 2`，原结构转为 `VehicleRig`，玩家自动入座。
4. 之后按 W/S 油门、鼠标/按键控制三轴舵面；飞行/行驶/滑行全部由同一套 6DOF 内核驱动。

### 6.2 装配算法（BFS → 蓝图 → 刚体）

```
assemble(root):
  1  BFS 6-邻接收集连通方块；过滤 空气/流体/可替换植物/黑名单
     上限 max_voxels（默认 512），跨接仅允许 mv:connector 标签方块
  2  VoxelSet → Blueprint：相对坐标 + BlockState + 密度 ρ + 功能标签
  3  质量属性：m、质心 c、惯性张量 I（含非对角项）
  4  几何：S / b / c̄ / AR / 碰撞 OBB 集合 / 参考点 = 质心
  5  部件扫描：按 blocks.yml 的 tag → Part 实例（推力、翼、舵、轮、座位、能源、挂接点）
  6  生成 VehicleRig，注册到 VehicleWorld，序列化蓝图，广播 spawn
  7  原方块处理策略可配：保留为碰撞代理 / 移除并交给客户端渲染（默认）
```

**边界规则**：无座椅不装配；体素超限给出明确原因；含 `mv:no_assemble` 标签的方块视为结构边界；装配失败不破坏原方块。

### 6.3 功能方块标签

| 标签 | 作用 | 主要参数 |
|---|---|---|
| `mv:seat` | 驾驶座 / 乘客座 | 是否主驾、视角偏移、权限 |
| `mv:thruster` | 推进器 | 推力轴向、$T_{max}$、能耗、矢量范围、是否差动 |
| `mv:wing` | 升力面 | $S$、$C_{L\alpha}$、$\alpha_{stall}$、$AR$、安装角 |
| `mv:control_surface` | 舵面 | 类型(升降/副翼/方向)、$\delta_{max}$、响应速率 |
| `mv:wheel` / `mv:bogie` | 轮组/转向架 | 接触点、$\mu$、悬挂 $k,c$、$r_w$、$J$ |
| `mv:connector` | 挂接点 | 朝向、$F_{hook}^{max}$、连挂半径 |
| `mv:energy` | 能源 | 容量、峰值功率、补给类型 |
| `mv:ballast` | 配重 | 额外质量（用于调质心） |

未打标签的普通方块只贡献**质量、惯量、碰撞与外观**——这正是"放到对应方块上就能当载具"的含义：哪怕全用石头搭，也能飞/滑，只是没推力没舵。

### 6.4 蓝图与数据驱动

蓝图序列化为 JSON（体素表 + 部件表 + 派生参数 + 版本），存 `plugins/MoreVehicles/blueprints/`；`/mv blueprint save <name>` / `spawn <name> [world x y z]`；支持资源包模型 id 覆盖外观。玩家可用 `vehicle_configs/<blueprint>.json` 覆盖任意气动/动力系数。

### 6.5 控制与多人

归一化控制量：throttle(W/S)、pitch、yaw(A/D)、roll(Q/E)、brake(空格)、flap/gear(F/G)、矢量推力；多座位乘客、owner 授权、跨维度挂起/恢复（区块卸载时持久化状态，重载后恢复）。

---

## 7. 客户端表现

### 7.1 渲染

Fabric 侧用**纯客户端实体**（只在 `ClientWorld` 添加，不注册服务端 `EntityType`），驱动体素/Blockbench 模型；1.21.11 遵守 Extract/Draw 分离，几何写入缓冲在提取阶段完成，绘制阶段不读世界状态。收到 `spawn` 后隐藏服务端 Display 组，避免双重模型。

### 7.2 预测 / 插值 / 校正

见 3.5。校正用指数平滑而非硬设位置；脱轨瞬间允许一次大误差硬同步（配合粒子掩盖）。

### 7.3 HUD

空速/地速、攻角 $\alpha$、侧滑 $\beta$、油门、能量、$L/V$ 风险条、编组状态（几节车 / 车钩受力）、失速与脱轨预警。

### 7.4 降级（未装 Mod 的客户端）

服务端用 `BlockDisplay`（方块外观）+ `ItemDisplay`（模型件）按刚体变换拼装，设置 `Transformation` / `setTransformationMatrix(Matrix4f)` + `interpolationDuration` 平滑；`Interaction` 实体提供可点击命中盒。控制退化为原版输入事件（右键上车、潜行下车、方向键近似油门），可完整看到矿车与载具移动，但不提供高精度物理与 HUD。

---

## 8. 工程结构、配置与性能

```
more-vehicles/
├─ common/                # 纯 Java：物理内核 / 协议字节 / 蓝图模型（无 MC 依赖，可单测）
│  ├─ physics/  AeroSolver, RigidBody6DOF, Coupling, DavisResistance, DerailmentModel
│  ├─ blueprint/ VoxelSet, Blueprint, MassProperties
│  └─ protocol/  MVSpec, MVCodecs, PacketType
├─ server/                # Purpur 插件（paper-plugin.yml, Java 21）
│  ├─ VehicleWorld, RailGraph, TrainSet, VehicleRig, Blueprinter
│  ├─ MVProtocol(Bukkit Messenger), SnapshotBus, DisplayRig
│  └─ commands/, listeners/, config.yml, blocks.yml, vehicle_configs/
└─ client/                # Fabric Mod（environment: client）
   ├─ Handshake, InputSampler, Predictor, SnapshotBuffer
   ├─ ClientVehicleEntity, VehicleRenderer, HUD
   └─ mixin/ FallbackProviderHook（仅在跨端 codec 被拦截时启用）
```

`config.yml` 关键项：`gravity_scale`、`air_density`、`integrator`、`snapshot_hz`、`derailment.mode`、`nadal_limit`、`flange_angle`、`friction`、`max_voxels`、`protocol_version`、`fallback_mixin`。

性能约束：物理计算不产生每 tick 垃圾对象（复用状态对象/原始数组）；按区块空间索引载具；快照批量合并；重计算（曲率拟合、凸包）只在装配时做；Display 实体数量设上限并按距离剔除；主线程只做积分与发包，序列化可异步但**广播回主线程**。

---

## 9. 分阶段路线（对应提示词包 P0~P9）

| 阶段 | 交付 | 验收口径 |
|---|---|---|
| P0 | 双工程脚手架 + 基线核验 | 两端能启动、Java 21、`~1.21.11` 依赖正确、`/mv version` 有输出 |
| P1 | 协议层与握手 | Fabric 客户端 JOIN 后收到 `hello_ack`；S2C 不再静默丢弃 |
| P2 | 纯 Java 6DOF 内核 | JUnit： vacuum 抛体、四元数归一化、惯量/力矩对称性、能量守恒边界 |
| P3 | RailGraph + 基础矿车 | 直轨/弯轨/坡道/道岔上按弧长正确运动 |
| P4 | 编组 + 动力矿车 + $\kappa$ 函数 | 编组车钩力不发散；`/mv dynacurve` 曲线与理论平衡点一致 |
| P5 | 脱轨模型 | 小半径高速必触发，低速大半径不触发；概率可复现 |
| P6 | 驾驶座 → 蓝图装配 | 石头堆也能装配；质心/惯量/面积与解析值一致 |
| P7 | 气动部件与控制映射 | 攻角超 $\alpha_{stall}$ 失速；差动推力产生偏航 |
| P8 | Fabric 渲染/预测/HUD | 100 ms 延迟下无明显抖动，校正不瞬移 |
| P9 | 降级 Display、命令、性能、回归 | vanilla 客户端可见载具；50 台载具 20 TPS 稳定；全量回归通过 |

---

## 10. 风险与待验证项

- **跨端 codec 兼容是最大不确定性**：Fabric 1.21.11 对 Bukkit 原始 `custom_payload` 的 fallback 行为需实测；方案已给"完整注册 + Mixin 兜底"两级解法，P1 必须先做最小连通性验证再写业务包。
- 若走代理（Velocity/Bungee），`bungeecord:main` 可能改写通道，需在 P1 增加代理标记位。
- 1.21.11 的具体 mapping（Yarn/Mojmap）与 `CustomPacketPayload.Type` / `Id` 命名在落地时以实际依赖为准；提示词要求 AI **先读 jar/Javadoc 再写代码**，不得凭记忆硬写类名。
- Purpur 非 Folia，可用主线程 scheduler；若未来换 Folia，需改为区域化调度。
- Display 实体数量多时有带宽成本，必须做距离剔除与合并。
- 物理"真实化"与原版手感冲突：默认参数偏原版，硬核参数单独一档，避免一上手就难开。

---

## 附：参考来源（供 AI 在 P0 复核）

- Fabric Documentation 1.21.11 — Project Structure / `fabric.mod.json` / 网络通信 / 在世界中渲染：`https://docs.fabricmc.net/1.21.11/develop/`
- Fabric Wiki — 从 1.20.5 起需要 Java 21：`https://wiki.fabricmc.net/zh_cn:tutorial:setup`
- PurpurMC 官方仓库 `ver/1.21.11` 与 `api.purpurmc.org`：`https://github.com/PurpurMC/Purpur`
- PaperMC Docs — Display entities / Transformation / interpolation：`https://docs.papermc.io/paper/dev/display-entities/`
- FabricMC GitHub Discussion #4430 — Spigot → Fabric 1.21 自定义 payload 收不到与 FallbackProvider Mixin 变通
- Fabric API Javadoc — `PayloadTypeRegistry` / `ServerPlayNetworking` / `ClientPlayNetworking`
- Davis 方程与 Curtius–Kniffler 黏着系数：MDPI *Design of Train Speed Trajectory Diagrams* (2025)
- Nadal / Wagner L/V 公式：Wikipedia *Nadal formula*；APTA PR-M-S-015-06 Annex A
- 均衡超高与欠超高：铁路轨道几何（$C_{eq}=GV^2/127R$）
- 6DOF 与气动导数：BYU FLOW Lab *SixDOF.jl Theory*；F-16 6DoF 仿真方程

---

## 11. 三域物理扩展：空中 / 水面 / 水下（真实物理）

> 本节是对第 4~9 节的**强制性修订**：不是给载具"加个浮力开关"，而是把载具放进一个连续的流体介质场里。任何实现若把"入水"写成布尔切换、或把水下当作"空气阻力×常数"，一律视为不合格。

### 11.1 统一原则

一套状态、一套积分器、一套 `ForceSink` 接口，三域差异只来自介质采样：

- `FluidMedium.sample(worldPos, tick)` → `{介质类型, ρ, μ, u_flow, p_static, 饱和度 α}`。
- **按部件分部采样**：每个 Part 用自己变换后的世界坐标取介质，算完力/力矩再求和。同一 tick 里"机翼在空气、船体在水、螺旋桨半浸没"是常态，禁止给整台载具一个全局 `isInWater`。
- 相对流速 $\mathbf v_{rel}=R(q)\mathbf v_b-\mathbf u_{flow}$；所有 $\bar q$、$Re$、$Fr$、$J$、$\sigma$ 一律基于 $v_{rel}$（逆流掉速、顺风增速、气泡柱抬升都自然出现）。
- 6DOF 方程不变（第 4.1 节），只是把 $m\to m+\alpha m_a$、$\mathbf I\to\mathbf I+\alpha\mathbf I_a$，并新增浮力/兴波/入水冲击力汇。

### 11.2 介质场：把方块世界映射成流体

| 介质 | 密度（质量单位/方块³） | 附加项 |
|---|---|---|
| 空气 | $\rho_{air}(h)=\rho_0 e^{-h/H}$，$\rho_0\approx1.2\times10^{-3}$，$H$ 可配 | 风场（梯度风 + 确定性阵风噪声）、地效区 |
| 水 | $\rho_w=1.0$ | 洋流 $\mathbf u_{flow}$（由水流方块/气泡柱推断）、自由面高度 $y_s$、静压 $p=p_0+\rho_w g h$ |
| 岩浆 | $\rho_{lava}\approx2.7$、$\mu$ 高 | 高黏滞阻尼、灼烧事件 |
| 气泡柱 / 灵魂沙 | 同水 | 附加向上 $\mathbf u_{flow}$，可托起载具 |
| 含水方块（waterlogged） | 不计入流体体积 | 只算碰撞代理，避免"楼梯里游泳" |

**浸没比连续化**：$\alpha=\text{smoothstep}(d/\varepsilon)$，$d$ 为部件中心到自由面的有符号距离、$\varepsilon\approx0.25$ 方块。$\rho_{eff}=\alpha\rho_w+(1-\alpha)\rho_{air}$；$C_D$、$C_a$、舵效、推进效率全部按 $\alpha$ 插值。水面高度由连续水方块顶面 + 水位高度插值得到，避免台阶式跳变。

### 11.3 浮力、浮心与初稳性（船会不会翻是算出来的）

$$
\mathbf F_b=\rho_w g\,\nabla\,\hat{\mathbf y},\qquad \nabla=V_{sub}(h),\qquad
\mathbf M_b=(\mathbf B-\mathbf G)\times\mathbf F_b
$$

$\mathbf B$ 为浸没体积形心（浮心），$\mathbf G$ 为质心；二者不重合即产生扶正/倾覆力矩。

**初稳性（Bouguer）**：

$$
BM=\frac{I_{wp}}{\nabla},\quad GM=KB+BM-KG,\quad
M_{right}=\rho_w g\,\nabla\cdot GM\cdot\sin\varphi\quad(|\varphi|\lesssim10^\circ)
$$

- $I_{wp}$ 为水线面关于倾斜轴的二次矩；小角度用上式，大倾角走装配时预计算的 **GZ 曲线**（查表）。
- $GM>0$ 自动回正；$GM<0$ 会**真的倾覆**；$GM$ 过大则横摇又硬又快，需配横摇阻尼 $C_{roll}$。
- **自由液面修正**：压载舱未满时 $GM_{corr}=GM-\sum FSM/\Delta$，半舱水晃动会削弱稳性。
- **可压缩浮力舱 / 气囊**：$V(h)=V_0\,p_0/(p_0+\rho g h)$（等温近似），下潜越深浮力越小 → 潜艇/飞艇的天然升限与压载手感。
- **压载水舱** `mv:ballast_tank`：注水改变总质量与 $\mathbf G$，实现下潜、上浮、纵倾配平；抽排可逆。

### 11.4 附加质量（水下"变沉"的来源）

$$
m_a=C_a\,\rho\,\nabla,\qquad \mathbf I_a=\mathbf C_{a}^{rot}\rho\nabla L^2
$$

船体加速必须带动周围流体，$m_a$ 以**惯性项**进入方程（$F=(m+m_a)a$），匀速时为零——与阻力完全不同。细长体经验值：轴向 $C_a\approx0.1$、横向 $C_a\approx1.0$、球体 $0.5$、方柱横向约 $1.19$；方向插值 $C_a(\theta)=0.1+0.9\sin^2\theta$。水下起步、制动、转向明显迟滞，这是"真实水感"的核心。

**密度表（对 4.4 的修订）**：$\rho_w=1.0$、$\rho_{stone}=2.4$、$\rho_{wood}=0.6$、$\rho_{iron}=7.8$、$\rho_{air}=0.0012$。于是木结构自然漂浮、石结构必须靠浮力舱或排水体积才能浮——全石头搭的载具不会莫名漂起来。

### 11.5 阻力：黏性 + 形状 + 兴波

$$
R=\tfrac12\rho v_{rel}^2 S\,C_T,\qquad
C_T=(1+k)C_F(Re)+C_{form}+C_W(Fr)+C_A
$$

- $k$ 为 ITTC-1978 形因子（细体 ~0.10，肥体 ~0.30）；$C_F$ 用 ITTC-1957 平板摩擦线；$C_A$ 粗糙度裕度。
- **Froude 数** $Fr=V/\sqrt{gL_{wl}}$：兴波阻力在 $Fr\approx0.30$ 与 $\approx0.50$ 出现峰谷，$Fr\approx0.40$ 是排水型船体的实际上限（船在爬自己的艏波）；$Fr>0.7$ 进入**滑行态**，水动升力接管、$C_W$ 骤降。这段曲线让"船越快越费劲、快到一定程度突然轻快"真实可感。
- 完全浸没时 $C_W\to0$（无自由面），近水面保留自由面效应；尾流/水花粒子强度由 $Fr$ 与 $\alpha$ 驱动。

### 11.6 水翼、水舵与空化

水翼复用 4.2 的 $C_L/C_D/C_m$ 导数公式，**只把 $\rho$ 换成 $\rho_w$（约为空气的 800 倍）**：小面积即可产生巨大升力，水翼艇能抬出水面减阻。

**空化数**（水翼/螺旋桨的限速机制）：

$$
\sigma=\frac{p_\infty-p_v}{\tfrac12\rho V^2},\qquad p_\infty=p_0+\rho_w g h
$$

$\sigma<\sigma_{crit}$（可配，约 1~2）时 $C_L$ 骤降、$C_D$ 上升、推力跌落，伴随气泡粒子与噪声事件；**潜深增加会提高 $p_\infty$、缓解空化**——这是水下高速必须下潜的真实物理。水舵舵效 $\propto v^2$，低速几乎无舵效，需侧推器或差动推力。

### 11.7 推进按介质分流

| 类型 | 模型 | 约束 |
|---|---|---|
| 喷气 `mv:jet` | $T=T_{max}\cdot u\cdot(1-v/v_{max})^n$，带 spool-up 惯性 | **入水失效**（可配效率惩罚），水下不能用 |
| 空气螺旋桨 / 旋翼 | $T=\rho_{air} n^2D^4K_T(J)$，$J=V_A/(nD)$ | 入水效率暴跌 |
| 水中螺旋桨 `mv:propeller` | $T=\rho_w n^2D^4K_T(J)$，$Q=\rho_w n^2D^5K_Q$，$\eta_0=\dfrac{J K_T}{2\pi K_Q}$ | $K_T$ 用 Wageningen B 系列简化拟合 $K_T\approx K_{T0}-kJ$，随 $J$ 上升而下降 → 高速推力自然衰减 |
| 喷水 `mv:waterjet` | 同上，吸水口必须 $\alpha>0.8$ | 出水吸气失推 |
| 两栖 `mv:amphibious` | 双套曲线按 $\alpha$ 插值 | 过渡区效率谷 |

$\mathbf T$ 仍按第 4.1 节含偏心 $\mathbf r\times\mathbf T$ 力矩；差动推力在水下同样能偏航（但受附加质量阻尼，转向更钝）。风场/洋流进入 $v_{rel}$：帆 `mv:sail` 靠风、逆流掉速、气泡柱抬升，全部服务端确定性随机，可复现。

### 11.8 轻于空气的飞行体（第二类"天上飞的"）

$$
F_{net}=\big(\rho_{air}(h)-\rho_{gas}\big)gV_{gas}-m_{envelope}g
$$

- 净浮力随高度按 $\rho_{air}(h)$ 衰减 → **自然升限**；气囊放气/可压缩体积可控升降。
- $C_L\approx0$，附加质量按空气 $\rho$ 计（仍不可忽略，飞艇掉头很钝）；推进靠空气桨或纯风漂移。
- 重于空气的机翼载具补：**地效**（离地 $<b/2$ 时 $C_{D_i}$ 乘 $<1$ 系数）、**密度随高度**（高空升力与推力衰减）、**阵风/风切变**（确定性噪声）、**尾旋**（大迎角 + 侧滑耦合，配置开关）。

### 11.9 跨域过渡（最容易数值爆炸的地方）

1. **分部采样**：同 tick 各 Part 各自取介质，$\Sigma$ 后再积分；绝不整体切模式。
2. **连续插值**：$\rho_{eff}$、$C_D$、$C_a$、舵效、推进效率、浮力全部按 $\alpha$ 平滑，无阶跃。
3. **入水冲击 slamming**：额外脉冲 $F_{slam}=\tfrac12\rho_w A_{wet}v_\perp^2 C_s$，作用 ≤2 tick、冲量上限钳制；出水带吸附力与尾滴水事件。
4. **子步与钳制**：检测到 $\alpha$ 跨 0/1 的帧自动切 4 个子步；单步力/力矩上限；浮力与重力用隐式阻尼抑制水面上下振荡（bobbing）。
5. **脱轨落水复用本求解器**（修订 5.4）：矿车冲出轨道落水后，按浮力 + 附加质量 + 水阻成为自由 6DOF 刚体，可沉底、可漂浮、可 `/mv rerail`；水下轨道段则按水阻修正 $\kappa$ 函数。

### 11.10 装配时的派生参数与部件标签（修订 4.5 / 6.3）

装配阶段**一次性**预计算并缓存（运行期只查表插值，每 tick 零重算）：

- 静水力曲线：$\nabla(h)$、$\mathbf B(h)$、$A_{wp}(h)$、$I_{wp}(h)$、$GZ(\varphi)$、$GM$、吃水线（按高度 0.05 方块步进的 LUT）。
- 质量：三轴 $C_a$、$\mathbf I_a$、压载舱自由液面矩 $FSM$。
- 几何：$L_{wl}$、湿表面积 $S_{wet}$、水线面、迎风面积（空中与水线以上分开）。

新增功能标签：

| 标签 | 作用 | 关键参数 |
|---|---|---|
| `mv:buoyancy` | 浮力舱 / 气囊 | $V_0$、可压缩性、气体密度 |
| `mv:ballast_tank` | 压载水舱 | 容量、注排速率、FSM |
| `mv:hydrofoil` | 水翼 | $S$、$C_{L\alpha}$、$\sigma_{crit}$、安装角 |
| `mv:rudder` | 水舵 | $\delta_{max}$、舵效系数、浸没要求 |
| `mv:propeller` | 螺旋桨 | $D$、$P/D$、$K_T(J)$ / $K_Q(J)$ 曲线、介质白名单 |
| `mv:jet` / `mv:waterjet` | 喷气 / 喷水 | 介质限制、spool-up |
| `mv:keel` | 龙骨 | 横摇阻尼、侧滑阻力 |
| `mv:sail` | 风帆 | 帆面积、风向响应 |

普通方块仍贡献质量、惯量、**排水体积与湿表面积**——这正是"一坨石头放个驾驶座就能当载具"的延续：它会沉，加浮力舱或做成船型中空才会浮。

### 11.11 协议、HUD、配置、降级（修订 3.3 / 7.3 / 8 / 9）

- `snapshot` 增字段：`medium`、`alpha`、`depth`、`V_sub`、`GM`、`Fr`、`sigma`、`ballast`、`p_static`；`event` 增：`splash_in` / `splash_out` / `cavitation` / `capsize` / `ballast_change` / `ground_effect`。
- HUD 三套仪表：飞行（空速、$\alpha$、$\beta$、升限密度、失速/地效）、航海（深度、吃水、浮力余量、GM 稳性条、Fr、空化预警、压载）、通用（能量、姿态、编组）。
- `config.yml` 增：`water_density`、`lava_density`、`air_density_sea`、`scale_height`、`vapor_pressure`、`surface_epsilon`、`slam_cap`、`substeps_on_entry`、`gm_min`、`free_surface_correction`、`wave_enabled`、`current_strength`、`wind_enabled`、`cavitation_enabled`。
- vanilla 降级：Display 组随浮沉/横摇同步；气泡、水花、尾流用粒子近似；Fabric 客户端做完整水面/气泡/尾流渲染与三套 HUD。
- 路线插入：**P2-F**（介质场 + 浮力稳性 + 附加质量内核）、**P7-F**（水动力 + 介质相关推进 + 跨域过渡）。

### 11.12 新增验收口径

| 项 | 通过标准 |
|---|---|
| 静水漂浮 | 平衡时 $\rho_w g\nabla=mg$，稳态吃水误差 < 2% |
| 初稳性 | $GM>0$ 受 15° 扰动后回正；$GM<0$ 自行倾覆；半舱压载使 $GM_{corr}$ 下降 |
| 附加质量 | 同样推力下水下加速度 ≈ 空气的 $1/(1+C_a\rho\nabla/m)$，误差 < 5% |
| 兴波阻力 | $Fr\approx0.30/0.50$ 出现阻力峰，$Fr\approx0.40$ 后功率代价陡升 |
| 空化 | $\sigma$ 低于阈值后 $C_L$ 单调下降；加深潜深可恢复 |
| 螺旋桨 | $K_T$ 随 $J$ 上升而下降，$\eta_0=JK_T/(2\pi K_Q)$ 与曲线自洽 |
| 跨域连续 | 100 次入水/出水无 NaN、无力跳变 > 钳制值、姿态角速度连续 |
| 半浸没 | 机翼在空气中、船体在水中时，两域各自受力且总力矩连续 |
| 压载 | 注水下潜 → 抽水上浮可逆，深度与质量关系单调 |
| 浮空器 | 净浮力随高度衰减并存在升限；放气可控下降 |

### 11.13 本节公式来源

- 初稳性 $GM=KB+BM-KG$、$BM=I_{wp}/\nabla$、扶正力矩、自由液面修正：Bouguer 稳性理论 / 船舶稳性手册
- 附加质量 $m_a=C_a\rho V$ 与细长体轴向 0.1 / 横向 1.0：Blevins *Applied Dynamics Handbook*、Hoerner、OrcaFlex 文档
- Froude 数、ITTC-1978 形因子 $(1+k)$、$Fr\approx0.4$ 排水型上限与 0.5 阻力峰：ITTC / Deephull / Heliciel 船体阻力资料
- 螺旋桨 $K_T=T/\rho n^2D^4$、$J=V_A/nD$、$K_Q$、$\eta_0$：MIT OCW 2.611 *Propeller Testing*、ITTC 敞水试验规程、Wageningen B 系列
- 空化数 $\sigma=(p_\infty-p_v)/\tfrac12\rho V^2$：MIT OCW、JVE 水翼空化数值研究
- 6DOF / 气动导数：BYU FLOW Lab *SixDOF.jl Theory*

### 11.14 对前文章节的落地修订清单

- **第 8 节工程结构**：`common/physics/` 新增 `MediumField`、`Hydrostatics`、`AddedMass`、`WaveResistance`、`CavitationModel`、`PropellerCurve`、`TransitionSolver`；`server/` 新增 `WaterSampler`（方块水位 / 洋流 / 气泡柱 / 岩浆判定）与 `BallastController`。`config.yml` 按 11.11 增项。
- **第 9 节路线表**插入两行（顺序在 P2 后、P7 后）：

| 阶段 | 交付 | 验收口径 |
|---|---|---|
| P2-F | 介质场 / 浮力稳性 / 附加质量 / 兴波 / 空化 / 螺旋桨 / 过渡内核 | 木块浮石沉、GM 符号正确、水下加速度比、Fr 峰、入水 100 次无 NaN |
| P7-F | 水动力 + 介质相关推进 + 跨域过渡接入真实载具 | 两栖全流程连续、深潜抑制空化、K_T 随 J 衰减、压载可逆 |

- **第 10 节风险**增补：① 入水瞬间是数值最不稳定点，必须子步 + 冲量钳制 + 阻尼，默认参数先保守；② 静水力 LUT 按 0.05 步进缓存，512 体素装配须控制内存与预计算耗时；③ 分部采样把每 tick 受力次数从 O(1) 变 O(部件数)，需按部件数上限与空间索引裁剪；④ 水面高度插值与 Display 组插值不同步会出现"模型插进水里"，两端要用同一 α 与 y_s；⑤ snapshot 新增字段增加带宽，按介质变化事件节流而非常驻全发。
- **第 3.3 / 7.3 节**：按 11.11 扩展 snapshot/event 字段与 HUD 三套仪表。
