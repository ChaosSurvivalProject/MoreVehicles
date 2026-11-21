package io.mv.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MVCodecsTest {

    @Test
    void varIntRoundTrip() {
        int[] values = {0, 1, 127, 128, 300, 65535, 1_000_000, Integer.MAX_VALUE};
        for (int v : values) {
            var sink = new MVCodecs.ByteArraySink();
            MVCodecs.writeVarInt(sink, v);
            var src = new MVCodecs.ByteArraySource(sink.toByteArray());
            assertEquals(v, MVCodecs.readVarInt(src), "varint " + v);
            assertFalse(src.hasRemaining());
        }
    }

    @Test
    void varIntEncodingMatchesSpec() {
        var sink = new MVCodecs.ByteArraySink();
        MVCodecs.writeVarInt(sink, 300);
        assertArrayEquals(new byte[]{(byte) 0xAC, 0x02}, sink.toByteArray());
    }

    @Test
    void primitivesRoundTripByteForByte() {
        var sink = new MVCodecs.ByteArraySink();
        MVCodecs.writeInt(sink, 0x01020304);
        MVCodecs.writeFloat(sink, 3.14159f);
        MVCodecs.writeDouble(sink, 2.718281828);
        byte[] bytes = sink.toByteArray();
        assertEquals(4 + 4 + 8, bytes.length);

        var src = new MVCodecs.ByteArraySource(bytes);
        assertEquals(0x01020304, MVCodecs.readInt(src));
        assertEquals(3.14159f, MVCodecs.readFloat(src), 1e-6);
        assertEquals(2.718281828, MVCodecs.readDouble(src), 1e-9);
    }

    @Test
    void stringRoundTripUtf8() {
        String[] texts = {"", "hello", "载具", "morevehicles:v1"};
        for (String t : texts) {
            var sink = new MVCodecs.ByteArraySink();
            MVCodecs.writeString(sink, t);
            var src = new MVCodecs.ByteArraySource(sink.toByteArray());
            assertEquals(t, MVCodecs.readString(src));
            assertFalse(src.hasRemaining());
        }
    }

    @Test
    void packetHeaderLayoutIsDeterministic() {
        // Simulate a snapshot header: VarInt type, int vehicleId, double x, float qw.
        int type = PacketType.SNAPSHOT.ordinalByte();
        var sink = new MVCodecs.ByteArraySink();
        MVCodecs.writeVarInt(sink, type);
        MVCodecs.writeInt(sink, 42);
        MVCodecs.writeDouble(sink, 1.5);
        MVCodecs.writeFloat(sink, 0.7071f);

        var src = new MVCodecs.ByteArraySource(sink.toByteArray());
        assertEquals(PacketType.SNAPSHOT, PacketType.fromOrdinal(MVCodecs.readVarInt(src)));
        assertEquals(42, MVCodecs.readInt(src));
        assertEquals(1.5, MVCodecs.readDouble(src));
        assertEquals(0.7071f, MVCodecs.readFloat(src), 1e-6);
        assertFalse(src.hasRemaining(), "cross-end byte layout must be exact");
    }
}
