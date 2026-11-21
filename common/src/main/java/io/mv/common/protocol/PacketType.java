package io.mv.common.protocol;

/** PacketType ordinal — single source of truth; must match docs/ProtocolSpec.md. */
public enum PacketType {
    HELLO,          // C2S, login once
    HELLO_ACK,      // S2C, once
    INPUT,          // C2S, 20 Hz
    SNAPSHOT,       // S2C, 10-20 Hz
    SPAWN,          // S2C, event
    DESPAWN,        // S2C, event
    EVENT,          // S2C, event
    BLUEPRINT,      // bidirectional, low frequency
    PING,           // C2S, debug
    PONG;           // S2C, debug

    public int ordinalByte() { return ordinal(); }

    public static PacketType fromOrdinal(int o) {
        PacketType[] v = values();
        if (o < 0 || o >= v.length) throw new IllegalArgumentException("Bad packet type: " + o);
        return v[o];
    }
}
