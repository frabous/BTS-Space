package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.AttackId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Server acknowledgement; deliberately a different wire type from SelectionRequestMessage. */
public final class SelectionStateMessage implements IMessage {
    private AttackId attack;
    public SelectionStateMessage() { }
    public SelectionStateMessage(AttackId attack) { this.attack = Objects.requireNonNull(attack, "attack"); }
    public AttackId getAttack() { return Objects.requireNonNull(attack, "Message not decoded"); }

    @Override
    public void toBytes(ByteBuf buf) {
        WireCodec.header(buf);
        buf.writeByte(getAttack().getWireId());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        WireCodec.requireHeader(buf, 2);
        attack = AttackId.fromWireId(buf.readUnsignedByte());
    }
}
