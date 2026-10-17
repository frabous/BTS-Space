package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.SelectionRequest;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class SelectionRequestMessage implements IMessage {
    private SelectionRequest request;
    public SelectionRequestMessage() { }
    public SelectionRequestMessage(SelectionRequest request) { this.request = Objects.requireNonNull(request, "request"); }
    public SelectionRequest getRequest() { return Objects.requireNonNull(request, "Message not decoded"); }

    @Override
    public void toBytes(ByteBuf buf) {
        WireCodec.header(buf);
        buf.writeByte(getRequest().getAttack().getWireId());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        WireCodec.requireHeader(buf, 2);
        request = new SelectionRequest(AttackId.fromWireId(buf.readUnsignedByte()));
    }
}
