# MoreVehicles 网络协议规范 v1

> 本文件是跨端字节布局的**单一来源**（MV-SPEC 3.4）。任何改动必须同时修改
> `common/src/main/java/io/mv/common/protocol/MVCodecs.java`、服务端 Messenger 写入、
> Fabric `StreamCodec`，并递增 `MVSpec.PROTOCOL_VERSION`，否则拒绝握手。

## 通道与阶段

- 命名空间：`morevehicles:v1`，**全部在 PLAY 阶段**（Bukkit 无 CONFIGURATION plugin message API）。
- S2C 坑：Bukkit 只向声明过通道的客户端下发；现代 Fabric 不发 `minecraft:register`。
  解法：Fabric JOIN 后主动发 C2S `hello`，服务端把玩家加入 `mvCapable` 并注册出站通道，
  再补发声明双保险。见 `docs/MV-SPEC.md` 3.2 坑 A/B/C/D。

## 字节布局（与 MVCodecs 逐字节一致）

| 类型 | 编码 | 说明 |
|---|---|---|
| `VarInt` | 无符号 LEB128，最多 5 字节 | 高位置 1 表示续字节 |
| `int` | 小端 4 字节 | vehicleId / seq |
| `double` | IEEE-754 小端 8 字节 | x, y, z |
| `float` | IEEE-754 小端 4 字节 | qx,qy,qz,qw；vx..wz；throttle/energy/derailRisk |
| `String` | `VarInt` 字节长度 + UTF-8 | 与 `PacketByteBuf#writeString` 一致 |
| 数组 | `VarInt count` + 元素序列 | 顺序固定 |

浮点字节序、枚举 ordinal、数组顺序全部由本文件定义，禁止两端各自解释。

## 包头

```
VarInt  packetType   // PacketType.ordinal()
```

`PacketType` 顺序（ ordinal 即线上值，禁止重排）：

```
0 HELLO         C2S  登录一次     protocolVersion(VarInt), capabilities(VarInt), proxyAware(bool)
1 HELLO_ACK     S2C  一次         serverProtocol(VarInt), tps(VarInt), gravityScale(double), enhanced(bool)
2 INPUT         C2S  20 Hz        vehicleId(int), seq(int), throttle/yaw/pitch/roll(float), brake(bool), clientTime
3 SNAPSHOT      S2C  10~20 Hz     vehicleId(int), tick(int), pos(3×double), quat(4×float), vel/omega(6×float), derailRisk(float), flags(VarInt)
4 SPAWN         S2C  事件         blueprintId/hash, 初始变换, Display 组 id
5 DESPAWN       S2C  事件         vehicleId(int)
6 EVENT         S2C  事件         eventType(VarInt), vehicleId(int), payload...
7 BLUEPRINT     双向  低频         name(String), version(VarInt), JSON 分块
8 PING          C2S  debug        （仅包头）
9 PONG          S2C  debug        （仅包头）
```

## 三域扩展字段（MV-SPEC 11.11，v1 预留）

`SNAPSHOT` 可选尾部按 flags 位图扩展：`medium, alpha, depth, V_sub, GM, Fr, sigma, ballast, p_static`。
`EVENT` 新增类型：`splash_in / splash_out / cavitation / capsize / ballast_change / ground_effect`。
未装 Fabric 的 vanilla 客户端走 Display 实体自带插值，不解析这些字段。

## 对拍测试

`common/src/test/.../MVCodecsTest` 已覆盖：VarInt 往返与规范字节（`300 -> AC 02`）、
定长原语逐字节往返、UTF-8 字符串、包布局确定性。P1 阶段需再补
`common encode -> server decode` 与 `common encode -> client decode` 逐字节一致测试。
