package fr.frabous.stellar.api;

import net.minecraft.entity.player.EntityPlayerMP;

/** Both calls run on the authenticated player's server world thread. */
public interface ServerRequestHandler {
    void select(EntityPlayerMP sender, SelectionRequest request);
    void launch(EntityPlayerMP sender, LaunchRequest request);
    void serverStopped();
}
