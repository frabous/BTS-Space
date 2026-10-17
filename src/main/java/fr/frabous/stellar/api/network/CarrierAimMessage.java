package fr.frabous.stellar.api.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Server-traced fire-control point, so witnesses see the batteries track it; no point = return to rest. */
public final class CarrierAimMessage implements IMessage {
    public UUID launchId;
    public boolean valid;
    public double x, y, z;
    public CarrierAimMessage() { }
    public CarrierAimMessage(UUID id, boolean valid, double x, double y, double z) {
        this.launchId = id; this.valid = valid; this.x = x; this.y = y; this.z = z;
    }
    @Override public void fromBytes(ByteBuf b) {
        if (b.readableBytes() != 41) throw new DecoderException("Invalid carrier aim length");
        launchId = new UUID(b.readLong(), b.readLong());
        int flag = b.readUnsignedByte();
        if (flag > 1) throw new DecoderException("Invalid carrier aim flag");
        valid = flag == 1; x = b.readDouble(); y = b.readDouble(); z = b.readDouble();
        for (double value : new double[] {x, y, z})
            if (!Double.isFinite(value) || Math.abs(value) > 30001000) throw new DecoderException("Invalid carrier aim point");
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeLong(launchId.getMostSignificantBits()); b.writeLong(launchId.getLeastSignificantBits());
        b.writeByte(valid ? 1 : 0); b.writeDouble(x); b.writeDouble(y); b.writeDouble(z);
    }
}
