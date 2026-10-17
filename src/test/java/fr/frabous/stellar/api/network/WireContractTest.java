package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class WireContractTest {
    @Test
    public void selectionRequestHasGoldenWireBytes() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new SelectionRequestMessage(new SelectionRequest(AttackId.RAILGUN)).toBytes(buffer);
            assertEquals(2, buffer.readableBytes());
            assertEquals(1, buffer.getUnsignedByte(0));
            assertEquals(1, buffer.getUnsignedByte(1));
            SelectionRequestMessage decoded = new SelectionRequestMessage();
            decoded.fromBytes(buffer);
            assertEquals(AttackId.RAILGUN, decoded.getRequest().getAttack());
        } finally { buffer.release(); }
    }

    @Test
    public void launchRequestPreservesNegativeDimensionsAndCoordinates() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            LaunchRequest source = new LaunchRequest(AttackId.RAILGUN, -1, new BlockTarget(-1440, 0, 400));
            new LaunchRequestMessage(source).toBytes(buffer);
            assertEquals(18, buffer.readableBytes());
            LaunchRequestMessage decoded = new LaunchRequestMessage();
            decoded.fromBytes(buffer);
            assertEquals(source.getAttack(), decoded.getRequest().getAttack());
            assertEquals(-1, decoded.getRequest().getDimension());
            assertEquals(source.getTarget(), decoded.getRequest().getTarget());
        } finally { buffer.release(); }
    }

    @Test
    public void stateRoundTripsEveryAttackAndPhase() {
        for (AttackId attack : AttackId.values()) {
            for (LaunchPhase phase : LaunchPhase.values()) {
                ByteBuf buffer = Unpooled.buffer();
                try {
                    LaunchState source = new LaunchState(new UUID(Long.MIN_VALUE, Long.MAX_VALUE), attack, 17,
                            new BlockTarget(29999984, 255, -29999984), Long.MIN_VALUE, Long.MAX_VALUE, phase);
                    new LaunchStateMessage(source).toBytes(buffer);
                    assertEquals(attack.carriesDeployment() ? 63 : 51, buffer.readableBytes());
                    LaunchStateMessage decoded = new LaunchStateMessage();
                    decoded.fromBytes(buffer);
                    LaunchState value = decoded.getState();
                    assertEquals(source.getLaunchId(), value.getLaunchId());
                    assertEquals(attack, value.getAttack());
                    assertEquals(17, value.getDimension());
                    assertEquals(source.getTarget(), value.getTarget());
                    assertEquals(source.getDeployment(), value.getDeployment());
                    assertEquals(Long.MIN_VALUE, value.getSeed());
                    assertEquals(Long.MAX_VALUE, value.getStartTick());
                    assertEquals(phase, value.getPhase());
                } finally { buffer.release(); }
            }
        }
    }

    @Test
    public void selectionAcknowledgementUsesOwnMessageType() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new SelectionStateMessage(AttackId.RAILGUN).toBytes(buffer);
            SelectionStateMessage decoded = new SelectionStateMessage();
            decoded.fromBytes(buffer);
            assertEquals(AttackId.RAILGUN, decoded.getAttack());
        } finally { buffer.release(); }
    }

    @Test public void blackholeSelectionUsesTheExistingTwoBytePacket() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new SelectionRequestMessage(new SelectionRequest(AttackId.BLACKHOLE)).toBytes(buffer);
            assertEquals(2, buffer.readableBytes());
            assertEquals(1, buffer.getUnsignedByte(0));
            assertEquals(2, buffer.getUnsignedByte(1));
            SelectionRequestMessage decoded = new SelectionRequestMessage();
            decoded.fromBytes(buffer);
            assertEquals(AttackId.BLACKHOLE, decoded.getRequest().getAttack());
        } finally { buffer.release(); }
    }

    @Test public void supernovaUsesWireIdSixAndTheExistingPacketLayouts() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new SelectionRequestMessage(new SelectionRequest(AttackId.SUPERNOVA)).toBytes(buffer);
            assertEquals(2, buffer.readableBytes());
            assertEquals(6, buffer.getUnsignedByte(1));
            SelectionRequestMessage selection = new SelectionRequestMessage(); selection.fromBytes(buffer);
            assertEquals(AttackId.SUPERNOVA, selection.getRequest().getAttack());
            buffer.clear();
            new LaunchRequestMessage(new LaunchRequest(AttackId.SUPERNOVA, -1, new BlockTarget(-12,64,34))).toBytes(buffer);
            assertEquals(18, buffer.readableBytes());
            assertEquals(6, buffer.getUnsignedByte(1));
            LaunchRequestMessage launch = new LaunchRequestMessage(); launch.fromBytes(buffer);
            assertEquals(AttackId.SUPERNOVA, launch.getRequest().getAttack());
            buffer.clear();
            new SelectionStateMessage(AttackId.SUPERNOVA).toBytes(buffer);
            SelectionStateMessage acknowledgement = new SelectionStateMessage(); acknowledgement.fromBytes(buffer);
            assertEquals(AttackId.SUPERNOVA, acknowledgement.getAttack());
        } finally { buffer.release(); }
    }

    @Test public void moonSelectionAndLaunchUseWireIdFiveWithoutChangingPacketSizes() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new SelectionRequestMessage(new SelectionRequest(AttackId.ASTEROID_PULLER)).toBytes(buffer);
            assertEquals(2, buffer.readableBytes());
            assertEquals(1, buffer.getUnsignedByte(0));
            assertEquals(5, buffer.getUnsignedByte(1));
            SelectionRequestMessage selection = new SelectionRequestMessage();
            selection.fromBytes(buffer);
            assertEquals(AttackId.ASTEROID_PULLER, selection.getRequest().getAttack());
            buffer.clear();
            LaunchRequest source = new LaunchRequest(AttackId.ASTEROID_PULLER, -1, new BlockTarget(-12, 64, 34));
            new LaunchRequestMessage(source).toBytes(buffer);
            assertEquals(18, buffer.readableBytes());
            assertEquals(1, buffer.getUnsignedByte(0));
            assertEquals(5, buffer.getUnsignedByte(1));
            LaunchRequestMessage launch = new LaunchRequestMessage();
            launch.fromBytes(buffer);
            assertEquals(AttackId.ASTEROID_PULLER, launch.getRequest().getAttack());
            assertEquals(source.getDimension(), launch.getRequest().getDimension());
            assertEquals(source.getTarget(), launch.getRequest().getTarget());
            buffer.clear();
            new SelectionStateMessage(AttackId.ASTEROID_PULLER).toBytes(buffer);
            assertEquals(2, buffer.readableBytes());
            assertEquals(5, buffer.getUnsignedByte(1));
            SelectionStateMessage acknowledgement = new SelectionStateMessage();
            acknowledgement.fromBytes(buffer);
            assertEquals(AttackId.ASTEROID_PULLER, acknowledgement.getAttack());
        } finally { buffer.release(); }
    }

    @Test public void presentationStateUsesIdenticalLaunchStateBytesAndPreservesTheFullSnapshot() {
        ByteBuf buffer = Unpooled.buffer(), canonical = Unpooled.buffer();
        try {
            for (AttackId attack : AttackId.values()) {
                for (LaunchPhase phase : LaunchPhase.values()) {
                    buffer.clear(); canonical.clear();
                    LaunchState source = new LaunchState(new UUID(Long.MIN_VALUE, Long.MAX_VALUE), attack, -1,
                            new BlockTarget(-29999984, 255, 29999984), Long.MIN_VALUE, Long.MAX_VALUE, phase);
                    new PresentationStateMessage(source).toBytes(buffer);
                    new LaunchStateMessage(source).toBytes(canonical);
                    assertEquals(attack.carriesDeployment() ? 63 : 51, buffer.readableBytes());
                    assertEquals(canonical, buffer);
                    PresentationStateMessage decoded = new PresentationStateMessage();
                    decoded.fromBytes(buffer);
                    LaunchState value = decoded.getState();
                    assertEquals(source.getLaunchId(), value.getLaunchId());
                    assertEquals(source.getAttack(), value.getAttack());
                    assertEquals(source.getDimension(), value.getDimension());
                    assertEquals(source.getTarget(), value.getTarget());
                    assertEquals(source.getDeployment(), value.getDeployment());
                    assertEquals(source.getSeed(), value.getSeed());
                    assertEquals(source.getStartTick(), value.getStartTick());
                    assertEquals(source.getPhase(), value.getPhase());
                    assertFalse(buffer.isReadable());
                }
            }
        } finally { buffer.release(); canonical.release(); }
    }

    @Test public void presentationStateRejectsEveryTruncationAndTrailingBytes() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            LaunchState source = new LaunchState(new UUID(1, 2), AttackId.ASTEROID_PULLER, 0,
                    new BlockTarget(256, 3, 256), 42, 100, LaunchPhase.MARKING);
            new PresentationStateMessage(source).toBytes(buffer);
            byte[] valid = new byte[buffer.readableBytes()];
            buffer.getBytes(0, valid);
            for (int length = 0; length < valid.length; length++) {
                buffer.clear(); buffer.writeBytes(valid, 0, length);
                assertBadPresentation(buffer);
            }
            buffer.clear(); buffer.writeBytes(valid); buffer.writeByte(0);
            assertBadPresentation(buffer);
        } finally { buffer.release(); }
    }

    @Test public void moonDeliveryIsDistinctFromImpactAndSurvivesPhaseUpdatesAndBothCodecs() {
        BlockTarget target = new BlockTarget(256,3,256), delivery = new BlockTarget(-42,70,23);
        LaunchState launch = new LaunchState(new UUID(1,2),AttackId.ASTEROID_PULLER,0,target,delivery,42,100,LaunchPhase.MARKING);
        for (LaunchPhase phase : LaunchPhase.values()) {
            LaunchState snapshot = launch.withPhase(phase);
            assertEquals(delivery,snapshot.getDeployment());
            assertEquals(target,snapshot.getTarget());
            ByteBuf buffer = Unpooled.buffer();
            try {
                new LaunchStateMessage(snapshot).toBytes(buffer);
                LaunchStateMessage state = new LaunchStateMessage(); state.fromBytes(buffer);
                assertEquals(delivery,state.getState().getDeployment());
                buffer.clear();
                new PresentationStateMessage(snapshot).toBytes(buffer);
                PresentationStateMessage film = new PresentationStateMessage(); film.fromBytes(buffer);
                assertEquals(delivery,film.getState().getDeployment());
                assertEquals(target,film.getState().getTarget());
                buffer.clear(); new PresentationStateMessage(snapshot).toBytes(buffer);
                buffer.setInt(55,256); assertBadPresentation(buffer);
            } finally { buffer.release(); }
        }
    }

    @Test public void presentationStateRejectsInvalidProtocolIdentityPhaseAndCoordinates() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            LaunchState source = new LaunchState(new UUID(1, 2), AttackId.ASTEROID_PULLER, 0,
                    new BlockTarget(256, 3, 256), 42, 100, LaunchPhase.MARKING);
            new PresentationStateMessage(source).toBytes(buffer);
            byte[] valid = new byte[buffer.readableBytes()];
            buffer.getBytes(0, valid);
            buffer.setByte(0, 2);
            assertBadPresentation(buffer);
            for (int attack : new int[]{0, 3, 4, 255}) {
                buffer.clear(); buffer.writeBytes(valid); buffer.setByte(17, attack);
                assertBadPresentation(buffer);
            }
            buffer.clear(); buffer.writeBytes(valid); buffer.setByte(50, 255);
            assertBadPresentation(buffer);
            buffer.clear(); buffer.writeBytes(valid); buffer.setInt(26, 256);
            assertBadPresentation(buffer);
            buffer.clear(); buffer.writeBytes(valid); buffer.setLong(42, -1);
            assertBadPresentation(buffer);
        } finally { buffer.release(); }
    }

    private void assertBadPresentation(ByteBuf buffer) {
        try {
            new PresentationStateMessage().fromBytes(buffer);
            fail("Malformed presentation packet accepted");
        } catch (IllegalArgumentException expected) {
            // The wrapper inherits all validation from the launch-state codec.
        }
    }

    @Test
    public void malformedPacketsAreRejected() {
        assertBad(new byte[] {1});
        assertBad(new byte[] {2, 1});
        assertBad(new byte[] {1, 0});
        assertBad(new byte[] {1, 3});
        assertBad(new byte[] {1, 4});
        assertBad(new byte[] {1, 1, 0});
    }

    private void assertBad(byte[] bytes) {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            new SelectionRequestMessage().fromBytes(buffer);
            fail("Malformed packet accepted");
        } catch (IllegalArgumentException expected) {
            // Invalid size, protocol or attack is a decode failure, never gameplay intent.
        } finally { buffer.release(); }
    }
}
