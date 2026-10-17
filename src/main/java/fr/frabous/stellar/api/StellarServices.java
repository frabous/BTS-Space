package fr.frabous.stellar.api;

import java.util.Objects;
import net.minecraft.entity.player.EntityPlayerMP;

/** Extension seam for the two implementation lots; default services perform no gameplay. */
public final class StellarServices {
    private static final ClientBridge NO_CLIENT = new ClientBridge() {
        public void enqueue(Runnable action) { }
        public void onSelection(AttackId attack) { }
        public void onLaunchState(LaunchState state) { }
        public void stopPresentation() { }
        public void reset() { }
    };
    private static final ServerRequestHandler NO_SERVER = new ServerRequestHandler() {
        public void select(EntityPlayerMP sender, SelectionRequest request) { }
        public void launch(EntityPlayerMP sender, LaunchRequest request) { }
        public void serverStopped() { }
    };
    private static volatile ClientBridge client = NO_CLIENT;
    private static volatile ServerRequestHandler server = NO_SERVER;

    private StellarServices() { }
    public static ClientBridge client() { return client; }
    public static ServerRequestHandler server() { return server; }
    public static void installClient(ClientBridge bridge) { client = Objects.requireNonNull(bridge, "bridge"); }
    public static void installServer(ServerRequestHandler handler) { server = Objects.requireNonNull(handler, "handler"); }
}
