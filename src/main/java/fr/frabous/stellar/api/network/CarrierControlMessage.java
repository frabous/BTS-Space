package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.CarrierGunneryRules;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Only intent and aim cross the trust boundary, never a target or damage amount. */
public final class CarrierControlMessage implements IMessage {
    public static final int FIRE = 0, EXIT = 1, AIM = 2;
    public UUID launchId;
    public int action;
    public float yaw, pitch;
    public CarrierControlMessage() { }
    public CarrierControlMessage(UUID id, int action, float yaw, float pitch) {
        this.launchId = id; this.action = action; this.yaw = yaw; this.pitch = pitch;
    }
    public boolean exit() { return action == EXIT; }
    @Override public void fromBytes(ByteBuf b) {
        if (b.readableBytes() != 25) throw new DecoderException("Invalid carrier control length");
        launchId = new UUID(b.readLong(), b.readLong());
        action = b.readUnsignedByte();
        if (action > AIM) throw new DecoderException("Invalid carrier action");
        yaw = b.readFloat(); pitch = b.readFloat();
        if (!CarrierGunneryRules.validAim(yaw, pitch)) throw new DecoderException("Invalid carrier aim");
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeLong(launchId.getMostSignificantBits()); b.writeLong(launchId.getLeastSignificantBits());
        b.writeByte(action); b.writeFloat(yaw); b.writeFloat(pitch);
    }
}
