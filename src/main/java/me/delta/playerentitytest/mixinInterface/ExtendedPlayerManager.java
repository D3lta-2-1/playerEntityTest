package me.delta.playerentitytest.mixinInterface;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;

public interface ExtendedPlayerManager {

    static ExtendedPlayerManager get(PlayerManager playerManager) //just a cast method
    {
        return (ExtendedPlayerManager) playerManager;
    }
    ServerPlayerEntity replaceBy(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer);
}
