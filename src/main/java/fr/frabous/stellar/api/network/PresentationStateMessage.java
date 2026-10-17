package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.LaunchState;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Caster-only presentation acknowledgement using the unchanged launch-state codec. */
public final class PresentationStateMessage implements IMessage {
    private final LaunchStateMessage message;

    public PresentationStateMessage() { message = new LaunchStateMessage(); }
    public PresentationStateMessage(LaunchState state) { message = new LaunchStateMessage(state); }
    public LaunchState getState() { return message.getState(); }

    @Override public void toBytes(ByteBuf buf) { message.toBytes(buf); }
    @Override public void fromBytes(ByteBuf buf) { message.fromBytes(buf); }
}
