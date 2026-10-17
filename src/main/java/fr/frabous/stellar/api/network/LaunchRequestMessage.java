package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.LaunchRequest;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class LaunchRequestMessage implements IMessage {
    private LaunchRequest request;
    public LaunchRequestMessage() { }
    public LaunchRequestMessage(LaunchRequest request) { this.request = Objects.requireNonNull(request, "request"); }
    public LaunchRequest getRequest() { return Objects.requireNonNull(request, "Message not decoded"); }

    @Override
    public void toBytes(ByteBuf buf) {
        WireCodec.header(buf);
        buf.writeByte(getRequest().getAttack().getWireId());
        buf.writeInt(request.getDimension());
        WireCodec.target(buf, request.getTarget());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        WireCodec.requireHeader(buf, 18);
        AttackId attack = AttackId.fromWireId(buf.readUnsignedByte());
        request = new LaunchRequest(attack, buf.readInt(), WireCodec.target(buf));
    }
}
