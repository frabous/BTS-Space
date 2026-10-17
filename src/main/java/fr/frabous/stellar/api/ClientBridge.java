package fr.frabous.stellar.api;

/** Physical-client boundary: no Minecraft client, renderer or LWJGL types here. */
public interface ClientBridge {
    default void preparePresentation() { }
    void enqueue(Runnable action);
    void onSelection(AttackId attack);
    void onLaunchState(LaunchState state);
    default void onPresentationState(LaunchState state) { onLaunchState(state); }
    default void onCarrierShot(CarrierShot shot) { }
    default void onCarrierAim(java.util.UUID launchId, boolean valid, double x, double y, double z) { }
    /** J calls this locally. It must not send a launch, abort, or damage packet. */
    void stopPresentation();
    /** Clear presentation on disconnect/world change, without changing server launches. */
    void reset();
}
