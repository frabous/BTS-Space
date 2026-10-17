package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.CarrierShot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class CarrierWireTest {
    private final UUID id = new UUID(Long.MIN_VALUE, Long.MAX_VALUE);
    @Test public void controlRoundTripsFireExitAndAimWithoutTrustingClientTargets() {
        ByteBuf b = Unpooled.buffer();
        try {
            for (int action : new int[]{CarrierControlMessage.FIRE, CarrierControlMessage.EXIT, CarrierControlMessage.AIM}) {
                b.clear(); new CarrierControlMessage(id, action, -179.5F, 89.5F).toBytes(b);
                assertEquals(25, b.readableBytes());
                CarrierControlMessage decoded = new CarrierControlMessage(); decoded.fromBytes(b);
                assertEquals(id, decoded.launchId); assertEquals(action, decoded.action);
                assertEquals(action == CarrierControlMessage.EXIT, decoded.exit());
                assertEquals(-179.5F, decoded.yaw, 0); assertEquals(89.5F, decoded.pitch, 0);
                assertFalse(b.isReadable());
            }
        } finally { b.release(); }
    }
    @Test public void everyControlTruncationTrailingByteInvalidActionAndAimAreRejected() {
        ByteBuf b = Unpooled.buffer();
        try {
            new CarrierControlMessage(id, CarrierControlMessage.FIRE, 0, 90).toBytes(b);
            for (int length = 0; length < 25; length++) badControl(b.slice(0, length));
            b.writeByte(0); badControl(b); b.writerIndex(25); b.readerIndex(0);
            b.setByte(16, 3); badControl(b); b.setByte(16, 0);
            b.setFloat(17, Float.NaN); badControl(b); b.setFloat(17, 0);
            b.setFloat(21, -91); badControl(b);
        } finally { b.release(); }
    }
    @Test public void serverShotRoundTripsAndRejectsMalformedBatteriesTimingAndPositions() {
        ByteBuf b = Unpooled.buffer();
        try {
            CarrierShot shot = new CarrierShot(id, -1, 4, 1, CarrierShot.TERRAIN, 120, 123, 131, -8, 228, 0.5, -200, 4, -20);
            new CarrierShotMessage(shot).toBytes(b); assertEquals(CarrierShotMessage.LENGTH, b.readableBytes());
            CarrierShotMessage decoded = new CarrierShotMessage(); decoded.fromBytes(b);
            assertEquals(id, decoded.shot.launchId); assertEquals(-1, decoded.shot.dimension);
            assertEquals(4, decoded.shot.battery); assertEquals(1, decoded.shot.barrel); assertEquals(CarrierShot.TERRAIN, decoded.shot.kind);
            assertEquals(120, decoded.shot.issued); assertEquals(123, decoded.shot.fire); assertEquals(131, decoded.shot.impact);
            assertEquals(shot.x, decoded.shot.x, 0); assertEquals(shot.hitX, decoded.shot.hitX, 0);
            assertEquals(shot.hitY, decoded.shot.hitY, 0); assertEquals(shot.hitZ, decoded.shot.hitZ, 0);
            for (int length = 0; length < CarrierShotMessage.LENGTH; length++) badShot(b.slice(0, length));
            b.readerIndex(0); b.writeByte(0); badShot(b); b.writerIndex(CarrierShotMessage.LENGTH); b.readerIndex(0);
            b.setByte(20, 5); badShot(b); b.setByte(20, 4);
            b.setByte(21, 2); badShot(b); b.setByte(21, 1);
            b.setByte(22, 3); badShot(b); b.setByte(22, 0);
            b.setLong(31, 119); badShot(b); b.setLong(31, 123);
            b.setLong(39, 122); badShot(b);
            b.setLong(39, 120 + CarrierShotMessage.MAX_SPAN + 1); badShot(b); b.setLong(39, 131);
            for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -30001001}) {
                b.setDouble(47, invalid); badShot(b);
            }
        } finally { b.release(); }
    }
    @Test public void aimRoundTripsBothStatesAndRejectsMalformedPoints() {
        ByteBuf b = Unpooled.buffer();
        try {
            for (boolean valid : new boolean[]{true, false}) {
                b.clear(); new CarrierAimMessage(id, valid, 12.5, 64, -9).toBytes(b);
                assertEquals(41, b.readableBytes());
                CarrierAimMessage decoded = new CarrierAimMessage(); decoded.fromBytes(b);
                assertEquals(id, decoded.launchId); assertEquals(valid, decoded.valid);
                assertEquals(12.5, decoded.x, 0); assertEquals(64, decoded.y, 0); assertEquals(-9, decoded.z, 0);
            }
            b.readerIndex(0);
            for (int length = 0; length < 41; length++) badAim(b.slice(0, length));
            b.setByte(16, 2); badAim(b); b.setByte(16, 1);
            b.setDouble(25, Double.NaN); badAim(b);
        } finally { b.release(); }
    }
    private static void badControl(ByteBuf source) {
        try { new CarrierControlMessage().fromBytes(source.duplicate()); fail("Malformed control accepted"); }
        catch (DecoderException expected) { }
    }
    private static void badShot(ByteBuf source) {
        try { new CarrierShotMessage().fromBytes(source.duplicate()); fail("Malformed shot accepted"); }
        catch (DecoderException expected) { }
    }
    private static void badAim(ByteBuf source) {
        try { new CarrierAimMessage().fromBytes(source.duplicate()); fail("Malformed aim accepted"); }
        catch (DecoderException expected) { }
    }
}
