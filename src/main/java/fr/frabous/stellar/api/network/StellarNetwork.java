package fr.frabous.stellar.api.network;

import fr.frabous.stellar.api.AttackId;
import fr.frabous.stellar.api.ClientBridge;
import fr.frabous.stellar.api.LaunchRequest;
import fr.frabous.stellar.api.LaunchState;
import fr.frabous.stellar.api.SelectionRequest;
import fr.frabous.stellar.api.StellarIds;
import fr.frabous.stellar.api.StellarServices;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Shared transport. No rendering, terrain writes or client-authored launch state. */
public final class StellarNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(StellarIds.NETWORK_CHANNEL);
    private static boolean registered;

    private StellarNetwork() { }

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.registerMessage(SelectionHandler.class, SelectionRequestMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(LaunchHandler.class, LaunchRequestMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(StateHandler.class, LaunchStateMessage.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(SelectionStateHandler.class, SelectionStateMessage.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(PresentationHandler.class, PresentationStateMessage.class, 4, Side.CLIENT);
        CHANNEL.registerMessage(CarrierControlHandler.class, CarrierControlMessage.class, 5, Side.SERVER);
        CHANNEL.registerMessage(CarrierShotHandler.class, CarrierShotMessage.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(CarrierAimHandler.class, CarrierAimMessage.class, 7, Side.CLIENT);
        registered = true;
    }

    public static void requestSelection(AttackId attack) {
        CHANNEL.sendToServer(new SelectionRequestMessage(new SelectionRequest(attack)));
    }

    public static void requestLaunch(LaunchRequest request) {
        CHANNEL.sendToServer(new LaunchRequestMessage(request));
    }

    public static void sendSelection(EntityPlayerMP player, AttackId attack) {
        CHANNEL.sendTo(new SelectionStateMessage(attack), player);
    }

    public static void sendState(EntityPlayerMP player, LaunchState state) {
        CHANNEL.sendTo(new LaunchStateMessage(state), player);
    }

    public static void sendPresentation(EntityPlayerMP player, LaunchState state) {
        CHANNEL.sendTo(new PresentationStateMessage(state), player);
    }

    public static void sendStateToDimension(LaunchState state) {
        CHANNEL.sendToDimension(new LaunchStateMessage(state), state.getDimension());
    }

    public static final class SelectionHandler implements IMessageHandler<SelectionRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(SelectionRequestMessage message, MessageContext context) {
            EntityPlayerMP sender = context.getServerHandler().player;
            SelectionRequest request = message.getRequest();
            sender.getServerWorld().addScheduledTask(() -> {
                if (sender.connection != null) StellarServices.server().select(sender, request);
            });
            return null;
        }
    }

    public static final class CarrierControlHandler implements IMessageHandler<CarrierControlMessage, IMessage> {
        @Override public IMessage onMessage(CarrierControlMessage message, MessageContext context) {
            EntityPlayerMP sender = context.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(() -> {
                if (sender.connection != null) fr.frabous.stellar.common.LaunchController.INSTANCE.controlCarrier(sender, message);
            });
            return null;
        }
    }

    public static final class CarrierShotHandler implements IMessageHandler<CarrierShotMessage, IMessage> {
        @Override public IMessage onMessage(CarrierShotMessage message, MessageContext context) {
            ClientBridge bridge = StellarServices.client();
            bridge.enqueue(() -> bridge.onCarrierShot(message.shot));
            return null;
        }
    }

    public static final class CarrierAimHandler implements IMessageHandler<CarrierAimMessage, IMessage> {
        @Override public IMessage onMessage(CarrierAimMessage message, MessageContext context) {
            ClientBridge bridge = StellarServices.client();
            bridge.enqueue(() -> bridge.onCarrierAim(message.launchId, message.valid, message.x, message.y, message.z));
            return null;
        }
    }

    public static final class LaunchHandler implements IMessageHandler<LaunchRequestMessage, IMessage> {
        @Override
        public IMessage onMessage(LaunchRequestMessage message, MessageContext context) {
            EntityPlayerMP sender = context.getServerHandler().player;
            LaunchRequest request = message.getRequest();
            sender.getServerWorld().addScheduledTask(() -> {
                if (sender.connection != null) StellarServices.server().launch(sender, request);
            });
            return null;
        }
    }

    public static final class StateHandler implements IMessageHandler<LaunchStateMessage, IMessage> {
        @Override
        public IMessage onMessage(LaunchStateMessage message, MessageContext context) {
            ClientBridge bridge = StellarServices.client();
            LaunchState state = message.getState();
            bridge.enqueue(() -> bridge.onLaunchState(state));
            return null;
        }
    }

    public static final class PresentationHandler implements IMessageHandler<PresentationStateMessage, IMessage> {
        @Override
        public IMessage onMessage(PresentationStateMessage message, MessageContext context) {
            ClientBridge bridge = StellarServices.client();
            LaunchState state = message.getState();
            bridge.enqueue(() -> bridge.onPresentationState(state));
            return null;
        }
    }

    public static final class SelectionStateHandler implements IMessageHandler<SelectionStateMessage, IMessage> {
        @Override
        public IMessage onMessage(SelectionStateMessage message, MessageContext context) {
            ClientBridge bridge = StellarServices.client();
            AttackId attack = message.getAttack();
            bridge.enqueue(() -> bridge.onSelection(attack));
            return null;
        }
    }
}
