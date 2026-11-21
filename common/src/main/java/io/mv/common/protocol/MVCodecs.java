package io.mv.common.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Byte-level codec implementing docs/ProtocolSpec.md. Both Bukkit Messenger (server)
 * and Fabric StreamCodec (client) must produce/consume EXACTLY these bytes.
 * Pure Java, no MC dependencies, so encode/decode can be cross-checked in unit tests.
 */
public final class MVCodecs {
    private MVCodecs() {}

    // ---------- VarInt (zigzag-free, unsigned LEB128, max 5 bytes) ----------
    public static void writeVarInt(ByteSink out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((byte) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((byte) v);
    }

    public static int readVarInt(ByteSource in) {
        int value = 0, shift = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IllegalArgumentException("VarInt too long");
    }

    // ---------- fixed primitives (little-endian, matching ByteBuf writeFloat/writeDouble) ----------
    public static void writeInt(ByteSink out, int v) {
        out.write((byte) v);
        out.write((byte) (v >>> 8));
        out.write((byte) (v >>> 16));
        out.write((byte) (v >>> 24));
    }

    public static int readInt(ByteSource in) {
        return (in.readUnsignedByte())
                | (in.readUnsignedByte() << 8)
                | (in.readUnsignedByte() << 16)
                | (in.readUnsignedByte() << 24);
    }

    public static void writeFloat(ByteSink out, float v) { writeInt(out, Float.floatToRawIntBits(v)); }
    public static float readFloat(ByteSource in) { return Float.intBitsToFloat(readInt(in)); }

    public static void writeDouble(ByteSink out, double v) {
        long bits = Double.doubleToRawLongBits(v);
        for (int i = 0; i < 8; i++) out.write((byte) (bits >>> (i * 8)));
    }

    public static double readDouble(ByteSource in) {
        long bits = 0;
        for (int i = 0; i < 8; i++) bits |= ((long) in.readUnsignedByte()) << (i * 8);
        return Double.longBitsToDouble(bits);
    }

    /** String = VarInt byte length + UTF-8 bytes (PacketByteBuf#writeString compatible). */
    public static void writeString(ByteSink out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    public static String readString(ByteSource in) {
        int len = readVarInt(in);
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) bytes[i] = (byte) in.readUnsignedByte();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ---------- minimal sink/source abstractions ----------
    public interface ByteSink {
        void write(int b);
        default void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) write(b[i]);
        }
    }

    public interface ByteSource {
        int readUnsignedByte();
    }

    /** Byte-array backed implementations used by tests and by the server Messenger bridge. */
    public static final class ByteArraySink implements ByteSink {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        public void write(int b) { bos.write(b); }
        public byte[] toByteArray() { return bos.toByteArray(); }
    }

    public static final class ByteArraySource implements ByteSource {
        private final byte[] data;
        private int pos;
        public ByteArraySource(byte[] data) { this.data = data; }
        public int readUnsignedByte() {
            if (pos >= data.length) throw new IllegalArgumentException("Buffer underflow");
            return data[pos++] & 0xFF;
        }
        public int position() { return pos; }
        public boolean hasRemaining() { return pos < data.length; }
    }
}
