# MoreVehicles 调参指南

> 所有系数在 `server/src/main/resources/config.yml` 与 `vehicle_configs/*.json`，
> 通过 `/mv reload` 热重载，无需重启。禁止在逻辑代码里散落魔法数字。

## 1. 动力系数 κ（MV-SPEC 5.3）

 traction（三段、低速无奇异）：

```
F_t(v) = min( ηP / max(v, v_min),  μ(v)·m_drive·g ) · (1 - (v/v_max)^n)_+
```

- `power`(P) 越大极速越高；`efficiency`(η) 线性缩放有效功率。
- `v_min` 消除 `P/v` 在 `v→0` 的奇异：**不要设为 0**（默认 0.5）。
- `v_max`/`n` 控制高速软限幅（非硬截断）：`n` 越大越接近硬截断。
- 黏着 `μ(v)`（Curtius–Kniffler，V 为 km/h）：干 `0.161+7.5/(V+44)`，
  湿 `0.130+7.5/(V+44)`。湿轨牵引力更低 → κ 骤降 + 打滑事件。

 κ 函数：

```
κ = [ΣF_t − ΣR_i(v) − g·sinθ·Σm_i − g·k_curve·R⁻¹·Σm_i − ΣF_c^‖] / (g·Σm_i^eff)
m_i^eff = m_i + J_i/r_w²
a_train = κ·g / (1 + λ(N−1))
```

- `train.curve_k`(k_curve)：曲线阻力，半径越小越显著。
- `train.lambda`(λ)：编组长度惩罚，越大长编组加速越钝。
- `wheel_radius`(r_w)：转动惯量折算 `J/r_w²`，越小起步越沉。
- `/mv dynacurve <id>` 输出 `v→κ` 表与平衡点 `v*`（κ=0）。`common` 的
  `PowerCurve.equilibriumSpeed` 用二分求 `v*`，与数值稳态误差应 <3%。

 Davis 阻力 `R(v)=A+Bv+Cv²`：A 机械滚阻、B 粘滞、C 气动。
 编组屏蔽：头车 C 大、中间车 C 小（shield），影响极速。

## 2. 脱轨（MV-SPEC 5.4）

```
(L/V)_lim = (tanδ − μ)/(1 + μ·tanδ)
r = (L/V)/(L/V)_lim
P = clamp(smoothstep(r−1))·(1 + γ·Cd⁺)
```

- `flange_angle`(δ) 增大 → 限值增大 → 更难脱轨。
- `friction`(μ) 增大 → 限值减小（分子减、分母增）→ 更容易脱轨。
- `nadal_limit`：保守工程值 0.5，游戏默认 0.8。
- `mode`：`off` / `probabilistic`（r<1 永不脱轨，默认）/ `hardcore`（r≥1 必脱轨）。
- 随机种子 = `vehicleId ^ tick`（splitmix64），**同种子必然复现**。

## 3. 气动（MV-SPEC 4.2）

```
CL = CL0 + CLα·α + CLq·q̂ + CLδe·δe   （|α|>α_stall 后在峰值处冻结并单调衰减）
CD = CD0 + CL²/(π·AR·e) + kδ|δ|
F_aero = q̄·S·[−CD, CY, −CL],  q̄ = ½ρV²
```

- `CLα` 越大升力越灵敏；`CD0` 决定寄生阻力与极速。
- `alpha_stall`：超过后 CL 从峰值单调衰减（**已实现为冻结峰值再衰减**，非无限增长后乘系数）。
- `AR=b²/S`、`oswald`(e)：诱导阻力项；e 越接近 1 越高效。

## 4. 三域流体（MV-SPEC 11）

| 参数 | 含义 | 默认 |
|---|---|---|
| `water_density` | ρ_w | 1.0 |
| `air_density_sea` | ρ_air(h)=ρ₀e^(−h/H) | 1.2e-3 |
| `scale_height` | 大气标高 H | 900 |
| `surface_epsilon` | α=smoothstep(d/ε) 的 ε | 0.25 |
| `slam_cap` | 入水冲击冲量上限 | 40 |
| `substeps_on_entry` | α 跨 0/1 的子步数 | 4 |

- **密度表**：木 0.6（自然浮）、石 2.4（沉）、铁 7.8、水 1.0。全石头结构不会莫名漂浮。
- 浮力 `F_b=ρ_w·g·∇` 作用于浮心 B；`GM=KB+BM−KG`、`BM=I_wp/∇`，
  `GM<0` 真的倾覆。半舱压载 `GM_corr=GM−ΣFSM/Δ` 削弱稳性。
- 附加质量 `m_a=C_a·ρ·∇` 进惯性项：轴向 0.1 / 横向 1.0，匀速不产生力。
- 兴波 `Fr=V/√(g·L_wl)`：0.30/0.50 阻力峰，0.40 后陡升，>0.7 进滑行态。
- 空化 `σ=(p_∞−p_v)/(½ρV²)`、`p_∞=p₀+ρ_w·g·h`：潜深增加可缓解空化。
- 螺旋桨 `T=ρn²D⁴K_T(J)`、`J=V_A/(nD)`、`η₀=J·K_T/(2πK_Q)`，K_T 随 J 增大而下降。

## 5. 热重载

`/mv reload` 重建 `ServerConfig`/`PhysicsConfig` 不可变快照，物理与协议参数立即生效。
**协议版本变更不能热重载**（必须两端同步并重启），因为涉及字节布局。
