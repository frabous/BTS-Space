package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.BroadsideBatteries;
import fr.frabous.stellar.api.CarrierShot;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class CarrierShotMessage implements IMessage {
    static final int LENGTH = 95;
    /** Slew, lock and flight together stay far below this; anything longer is malformed. */
    static final long MAX_SPAN = 400;
    public CarrierShot shot;
    public CarrierShotMessage() { }
    public CarrierShotMessage(CarrierShot shot) { this.shot = shot; }
    @Override public void fromBytes(ByteBuf b) {
        if (b.readableBytes() != LENGTH) throw new DecoderException("Invalid carrier shot length");
        UUID id = new UUID(b.readLong(), b.readLong()); int dimension = b.readInt();
        int battery = b.readUnsignedByte(), barrel = b.readUnsignedByte(), kind = b.readUnsignedByte();
        if (battery >= BroadsideBatteries.COUNT || barrel > 1 || kind > CarrierShot.AIR) throw new DecoderException("Invalid carrier battery");
        long issued = b.readLong(), fire = b.readLong(), impact = b.readLong();
        if (issued < 0 || fire < issued || impact < fire || impact - issued > MAX_SPAN) throw new DecoderException("Invalid carrier shot timing");
        double[] p = new double[6];
        for (int i = 0; i < p.length; i++) {
            p[i] = b.readDouble();
            if (!Double.isFinite(p[i]) || Math.abs(p[i]) > 30001000) throw new DecoderException("Invalid carrier shot position");
        }
        shot = new CarrierShot(id, dimension, battery, barrel, kind, issued, fire, impact, p[0], p[1], p[2], p[3], p[4], p[5]);
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeLong(shot.launchId.getMostSignificantBits()); b.writeLong(shot.launchId.getLeastSignificantBits());
        b.writeInt(shot.dimension); b.writeByte(shot.battery); b.writeByte(shot.barrel); b.writeByte(shot.kind);
        b.writeLong(shot.issued); b.writeLong(shot.fire); b.writeLong(shot.impact);
        b.writeDouble(shot.x); b.writeDouble(shot.y); b.writeDouble(shot.z);
        b.writeDouble(shot.hitX); b.writeDouble(shot.hitY); b.writeDouble(shot.hitZ);
    }
}
