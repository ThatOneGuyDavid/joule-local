package com.local.joulekompakt;

import java.util.*;
import java.nio.file.*;

public class ProtocolTests {
	static int count;
	static void check(boolean value, String name) { count++; if (!value) throw new AssertionError(name); }
	static byte[] hex(String text) { byte[] b = new byte[text.length()/2]; for (int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16); return b; }
	static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte v:b) s.append(String.format("%02x",v&255)); return s.toString(); }
	static void reject(byte[] b) { boolean rejected=false; try { Proto.fields(b); } catch (IllegalArgumentException e) { rejected=true; } check(rejected,"malformed protobuf must reject " + hex(b)); }
	public static void main(String[] args) throws Exception {
		int h=0x12345678;
		check(hex(Proto.envelope(h,120,Proto.EMPTY,Proto.EMPTY,Proto.EMPTY)).equals("0d785634122a003200c20700"),"empty-address pairing wire packet");
		check(hex(Proto.envelope(h,60,Proto.EMPTY,Proto.EMPTY,Proto.EMPTY)).equals("0d785634122a003200e20300"),"empty stop wire packet");
		for(long n:new long[]{0,1,127,128,255,16384,0xffffffffL,Long.MAX_VALUE}) check(Proto.number(Proto.fields(Proto.number(3,n)),3,-1)==n,"varint " + n);
		List<Proto.Field> point=Proto.fields(Proto.join(Proto.number(1,1),Proto.number(2,456),Proto.fixed(10,Float.floatToIntBits(55.5f)),Proto.number(11,3),Proto.number(4,2)));
		check(Proto.temperature(point)==55.5f,"temperature decode");
		check(Proto.number(point,11,-1)==3,"cooking state");
		check(Proto.number(point,4,-1)==2,"device error");
		check(Float.isNaN(Proto.temperature(Proto.fields(Proto.EMPTY))),"missing temperature not fabricated");
		for(byte[] bad:new byte[][]{hex("80"),hex("0d0000"),hex("0a05abcd"),hex("00"),hex("0b"),hex("088080808080808080808002"),hex("0affffffffffffffffff01")}) reject(bad);
		for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,101}) { boolean rejected=false; try { Proto.startBody(bad,1,1,null); } catch(IllegalArgumentException e) { rejected=true; } check(rejected,"invalid setpoint blocked"); }
		// Golden packets produced with the checked-out upstream Python implementation.
		for(String line:Files.readAllLines(Paths.get("tests/upstream-vectors.tsv"))) {
			String[] p=line.split("\t"); byte[] actual;
			switch(p[0]) {
				case "pair": actual=Proto.envelope(h,120,Proto.EMPTY,Proto.EMPTY,Proto.EMPTY); break;
				case "submit": actual=Proto.envelope(h,130,Proto.bytes(1,hex("0102030405060708090a0b0c")),Proto.EMPTY,Proto.EMPTY); break;
				case "feed": actual=Proto.envelope(h,70,Proto.number(1,1),Proto.EMPTY,Proto.EMPTY); break;
				case "stop": actual=Proto.envelope(h,60,Proto.EMPTY,Proto.EMPTY,Proto.EMPTY); break;
				case "identify": actual=Proto.envelope(h,152,Proto.EMPTY,Proto.EMPTY,Proto.EMPTY); break;
				case "compact": actual=Proto.envelope(h,50,Proto.startBody(57.22222f,1,345,null),Proto.EMPTY,Proto.EMPTY); break;
				case "full": actual=Proto.envelope(h,50,Proto.startBody(57.22222f,1,345,"00112233445566778899aabbccddeeff"),Proto.EMPTY,Proto.EMPTY); break;
				default: throw new AssertionError("Unknown vector");
			}
			check(Arrays.equals(actual,hex(p[1])),"upstream parity " + p[0]);
		}
		// Telemetry unknown fields must be tolerated, not confused with known fields.
		check(Proto.number(Proto.fields(Proto.join(Proto.bytes(400,hex("010203")),Proto.number(11,1))),11,-1)==1,"unknown-field tolerance");
		System.out.println("PASS: " + count + " protocol checks (including 7 upstream golden packets)");
	}
}
