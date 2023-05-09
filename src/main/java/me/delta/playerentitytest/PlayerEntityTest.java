package me.delta.playerentitytest;

import me.delta.playerentitytest.mixinInterface.ExtendedPlayerManager;
import net.fabricmc.api.ModInitializer;
import static net.minecraft.server.command.CommandManager.*;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerEntityTest implements ModInitializer
{

    private final Map<UUID, ServerPlayerEntity> serverPlayerEntityCopyMap = new HashMap<>();

    public static final String ID = "test";
    public static Logger logger = LoggerFactory.getLogger(ID);

    @Override
    public void onInitialize()
    {
        logger.info("test mod initialized");

        //TODO: concurently I've patched all crashes, but Skin layer is still broken, may be someone more familiar the data tracker can help me with that
        //TODO: armor point aren't updated too, must be a missing packet since clicking on the armor slot updates it

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("recreate")
                .executes(context -> {
                    var source = context.getSource();
                    var player = source.getPlayer();
                    if(player == null) return 1;

                    var playerCopy = serverPlayerEntityCopyMap.get(player.getUuid());
                    var playerManager = ExtendedPlayerManager.get(player.server.getPlayerManager());
                    if(playerCopy == null)
                    {

                        var playerInstance = new ServerPlayerEntity(player.server, player.getWorld(), player.getGameProfile());
                        var newPlayer= playerManager.replaceBy(player, playerInstance);
                        serverPlayerEntityCopyMap.put(player.getUuid(), player);
                        newPlayer.sendMessage(Text.literal("you got a new player instance"));
                    }
                    else
                    {
                        var newPlayer= playerManager.replaceBy(player, playerCopy);
                        serverPlayerEntityCopyMap.remove(player.getUuid());
                        newPlayer.sendMessage(Text.literal("you got your old player instance back"));
                    }
                    return 1;
                })));
    }
}
