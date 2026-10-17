package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.LaunchPhase;
import fr.frabous.stellar.api.LaunchState;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class LaunchStateMessage implements IMessage {
    private LaunchState state;
    public LaunchStateMessage() { }
    public LaunchStateMessage(LaunchState state) { this.state = Objects.requireNonNull(state, "state"); }
    public LaunchState getState() { return Objects.requireNonNull(state, "Message not decoded"); }

    @Override
    public void toBytes(ByteBuf buf) {
        WireCodec.header(buf);
        LaunchState value = getState();
        buf.writeLong(value.getLaunchId().getMostSignificantBits());
        buf.writeLong(value.getLaunchId().getLeastSignificantBits());
        buf.writeByte(value.getAttack().getWireId());
        buf.writeInt(value.getDimension());
        WireCodec.target(buf, value.getTarget());
        buf.writeLong(value.getSeed());
        buf.writeLong(value.getStartTick());
        buf.writeByte(value.getPhase().getWireId());
        if (value.getAttack().carriesDeployment()) WireCodec.target(buf, value.getDeployment());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int length = buf.readableBytes() >= 18
                && AttackId.carriesDeployment(buf.getUnsignedByte(buf.readerIndex()+17)) ? 63 : 51;
        WireCodec.requireHeader(buf, length);
        UUID id = new UUID(buf.readLong(), buf.readLong());
        AttackId attack = AttackId.fromWireId(buf.readUnsignedByte());
        int dimension = buf.readInt();
        BlockTarget target = WireCodec.target(buf);
        long seed = buf.readLong();
        long tick = buf.readLong();
        LaunchPhase phase = LaunchPhase.fromWireId(buf.readUnsignedByte());
        BlockTarget deployment = attack.carriesDeployment() ? WireCodec.target(buf) : target;
        state = new LaunchState(id, attack, dimension, target, deployment, seed, tick, phase);
    }
}
