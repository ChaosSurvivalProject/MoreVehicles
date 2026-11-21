package io.mv.common.protocol;

/**
 * Single source of truth for the wire format (MV-SPEC 3.4). Byte layout:
 * <pre>
 *   VarInt  packetType
 *   int     vehicleId / seq
 *   double  x, y, z
 *   float   qx,qy,qz,qw   (server guarantees normalized)
 *   float   vx,vy,vz, wx,wy,wz
 *   float   throttle/energy/derailRisk
 *   VarInt  flags / partBits
 *   VarInt  count -> variable-length array
 *   String  = VarInt length + UTF-8 bytes
 * </pre>
 * Any change must update BOTH ends and bump protocol version, else handshake is refused.
 */
public final class MVSpec {
    public static final String CHANNEL = "morevehicles:v1";
    public static final int PROTOCOL_VERSION = 1;

    private MVSpec() {}
}
