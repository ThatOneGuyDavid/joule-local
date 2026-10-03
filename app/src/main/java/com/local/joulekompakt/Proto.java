/* Joule protocol port derived from acato/ha-joule, Apache-2.0. See NOTICE. */
package com.local.joulekompakt;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Proto {
	public static final byte[] EMPTY = new byte[0];
	public static byte[] join(byte[]... pieces) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] piece : pieces) out.write(piece, 0, piece.length);
		return out.toByteArray();
	}
	public static byte[] var(long v) {
		if (v < 0) throw new IllegalArgumentException("Negative varint");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		while (v > 127) { out.write((int)(v & 127) | 128); v >>>= 7; }
		out.write((int)v); return out.toByteArray();
	}
	public static byte[] number(int field, long v) { return join(var(field << 3), var(v)); }
	public static byte[] bytes(int field, byte[] v) { return join(var((field << 3) | 2), var(v.length), v); }
	public static byte[] fixed(int field, int v) {
		return join(var((field << 3) | 5), ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array());
	}
	public static byte[] envelope(int handle, int field, byte[] body, byte[] sender, byte[] recipient) {
		return join(fixed(1, handle), bytes(5, sender), bytes(6, recipient), bytes(field, body));
	}
	public static byte[] startBody(float celsius, long feed, long sequence, String cookId) {
		if (!Float.isFinite(celsius) || celsius < 0 || celsius > 100) throw new IllegalArgumentException("Temperature out of range");
		byte[] program = join(fixed(1, Float.floatToIntBits(celsius)), number(5, 0));
		if (cookId != null) program = join(program, bytes(6, bytes(4, cookId.getBytes(java.nio.charset.StandardCharsets.UTF_8))), number(7, 0));
		return join(bytes(1, program), feed == 0 ? EMPTY : number(2, feed), sequence == 0 ? EMPTY : number(3, sequence));
	}
	public static class Field {
		public int id, wire; public long value; public byte[] data;
	}
	private static long readVar(byte[] data, int[] p) {
		long v = 0;
		for (int shift = 0; shift < 64; shift += 7) {
			if (p[0] >= data.length) throw new IllegalArgumentException("Truncated varint");
			int b = data[p[0]++] & 255;
			if (shift == 63 && (b & 254) != 0) throw new IllegalArgumentException("Varint overflow");
			v |= (long)(b & 127) << shift;
			if ((b & 128) == 0) return v;
		}
		throw new IllegalArgumentException("Varint overflow");
	}
	public static List<Field> fields(byte[] data) {
		if (data.length > 8192) throw new IllegalArgumentException("Message too large");
		List<Field> fields = new ArrayList<>(); int[] p = {0};
		while (p[0] < data.length) {
			long tag = readVar(data, p);
			if (tag <= 0 || tag > 0xffffffffL || tag >>> 3 == 0) throw new IllegalArgumentException("Invalid tag");
			Field f = new Field(); f.id = (int)(tag >>> 3); f.wire = (int)(tag & 7);
			if (f.wire == 0) f.value = readVar(data, p);
			else {
				long n = f.wire == 1 ? 8 : f.wire == 5 ? 4 : f.wire == 2 ? readVar(data, p) : -1;
				if (n < 0 || n > data.length - p[0]) throw new IllegalArgumentException("Invalid field length/type");
				f.data = Arrays.copyOfRange(data, p[0], p[0] + (int)n); p[0] += (int)n;
				if (f.wire == 5) f.value = ByteBuffer.wrap(f.data).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL;
			}
			fields.add(f);
		}
		return fields;
	}
	public static long number(List<Field> fs, int id, long fallback) {
		for (Field f : fs) if (f.id == id && f.wire == 0) return f.value;
		return fallback;
	}
	public static byte[] data(List<Field> fs, int id) {
		for (Field f : fs) if (f.id == id && f.wire == 2) return f.data;
		return null;
	}
	public static float temperature(List<Field> fs) {
		for (Field f : fs) if (f.id == 10 && f.wire == 5) return Float.intBitsToFloat((int)f.value);
		return Float.NaN;
	}
}
