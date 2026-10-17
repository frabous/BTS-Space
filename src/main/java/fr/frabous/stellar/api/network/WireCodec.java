package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.BlockTarget;
import fr.frabous.stellar.api.StellarIds;
import io.netty.buffer.ByteBuf;

final class WireCodec {
    private WireCodec() { }

    static void header(ByteBuf buf) { buf.writeByte(StellarIds.PROTOCOL_VERSION); }

    static void requireHeader(ByteBuf buf, int length) {
        if (buf.readableBytes() != length) throw new IllegalArgumentException("Invalid packet length");
        if (buf.readUnsignedByte() != StellarIds.PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Incompatible Beyond The Space protocol");
        }
    }

    static void target(ByteBuf buf, BlockTarget target) {
        buf.writeInt(target.getX());
        buf.writeInt(target.getY());
        buf.writeInt(target.getZ());
    }

    static BlockTarget target(ByteBuf buf) {
        return new BlockTarget(buf.readInt(), buf.readInt(), buf.readInt());
    }
}
