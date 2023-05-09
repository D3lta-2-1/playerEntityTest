package me.delta.playerentitytest.mixin;

import io.netty.buffer.Unpooled;
import me.delta.playerentitytest.PlayerEntityTest;
import me.delta.playerentitytest.mixinInterface.ExtendedPlayerManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.registry.tag.TagPacketSerializer;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerMetadata;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.biome.source.BiomeAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.entity.Entity.RemovalReason.CHANGED_DIMENSION;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin implements ExtendedPlayerManager {

    @Final @Shadow
    private MinecraftServer server;
    @Final @Shadow
    private List<ServerPlayerEntity> players;
    @Final @Shadow
    private CombinedDynamicRegistries<ServerDynamicRegistryType> registryManager;
    @Final @Shadow
    private Map<UUID, ServerPlayerEntity> playerMap;
    @Final @Shadow
    private DynamicRegistryManager.Immutable syncedRegistryManager;
    @Shadow
    private int viewDistance;
    @Shadow
    private int simulationDistance;
    @Shadow
    public abstract void sendCommandTree(ServerPlayerEntity player);
    @Shadow
    protected abstract void sendScoreboard(ServerScoreboard scoreboard, ServerPlayerEntity player);
    @Shadow
    public abstract void sendToAll(Packet<?> packet);
    @Shadow
    public abstract void sendWorldInfo(ServerPlayerEntity player, ServerWorld world);
    @Shadow
    public abstract void sendPlayerStatus(ServerPlayerEntity player);
    @Shadow
    public abstract int getMaxPlayerCount();

    //send default packets and properly set the player in the player manager
    void AddAndSendDefaultJoinPackets(ServerPlayerEntity player, boolean firstSpawn)
    {
        //this is the code just after the constructor of ServerPlayNetworkHandler in the onPlayerConnect method
        var serverPlayNetworkHandler = player.networkHandler;
        var world = player.getWorld();
        var worldProperties = world.getLevelProperties();
        GameRules gameRules = world.getGameRules();
        boolean doImmediateRespawn = gameRules.getBoolean(GameRules.DO_IMMEDIATE_RESPAWN);
        boolean ReducedDebugInfo = gameRules.getBoolean(GameRules.REDUCED_DEBUG_INFO);

        if(firstSpawn)
        {
            serverPlayNetworkHandler.sendPacket(new GameJoinS2CPacket(player.getId(), //this packet may could break the client, hopefully not
                    worldProperties.isHardcore(),
                    player.interactionManager.getGameMode(),
                    player.interactionManager.getPreviousGameMode(),
                    this.server.getWorldRegistryKeys(),
                    this.syncedRegistryManager,
                    world.getDimensionKey(), world.getRegistryKey(),
                    BiomeAccess.hashSeed(world.getSeed()),
                    this.getMaxPlayerCount(),
                    this.viewDistance,
                    this.simulationDistance,
                    ReducedDebugInfo,
                    !doImmediateRespawn,
                    world.isDebugWorld(),
                    world.isFlat(),
                    player.getLastDeathPos()));
            serverPlayNetworkHandler.sendPacket(new FeaturesS2CPacket(FeatureFlags.FEATURE_MANAGER.toId(world.getEnabledFeatures())));
            serverPlayNetworkHandler.sendPacket(new CustomPayloadS2CPacket(CustomPayloadS2CPacket.BRAND, (new PacketByteBuf(Unpooled.buffer())).writeString(this.server.getServerModName())));
            serverPlayNetworkHandler.sendPacket(new DifficultyS2CPacket(worldProperties.getDifficulty(), worldProperties.isDifficultyLocked()));
            serverPlayNetworkHandler.sendPacket(new PlayerAbilitiesS2CPacket(player.getAbilities()));


            serverPlayNetworkHandler.sendPacket(new SynchronizeRecipesS2CPacket(this.server.getRecipeManager().values()));
            serverPlayNetworkHandler.sendPacket(new SynchronizeTagsS2CPacket(TagPacketSerializer.serializeTags(this.registryManager)));
            //originally from firstSpawn = true if statement
            serverPlayNetworkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(player.getInventory().selectedSlot));

        }
        else
        {
            serverPlayNetworkHandler.sendPacket(new PlayerRespawnS2CPacket(player.world.getDimensionKey(), player.world.getRegistryKey(), BiomeAccess.hashSeed(player.getWorld().getSeed()), player.interactionManager.getGameMode(), player.interactionManager.getPreviousGameMode(), player.getWorld().isDebugWorld(), player.getWorld().isFlat(), (byte)1, player.getLastDeathPos()));
        }


        //originally from firstSpawn = false else statement
        serverPlayNetworkHandler.sendPacket(new DifficultyS2CPacket(worldProperties.getDifficulty(), worldProperties.isDifficultyLocked()));
        serverPlayNetworkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
        //serverPlayNetworkHandler.sendPacket(new HealthUpdateS2CPacket(player.getHealth(), player.getHungerManager().getFoodLevel(), player.getHungerManager().getSaturationLevel()));
        player.sendAbilitiesUpdate();

        this.sendCommandTree(player);
        this.sendPlayerStatus(player);
        player.getStatHandler().updateStatSet();
        player.getRecipeBook().sendInitRecipesPacket(player);
        this.sendScoreboard(world.getScoreboard(), player);

        Packet<?> packet = new EntityTrackerUpdateS2CPacket(player.getId(), player.getDataTracker().getDirtyEntries());
        sendToAll(packet);
        this.server.forcePlayerSampleUpdate();

        serverPlayNetworkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        ServerMetadata serverMetadata = this.server.getServerMetadata();

        if (serverMetadata != null) {
            player.sendServerMetadata(serverMetadata);
        }

        this.players.add(player);
        this.playerMap.put(player.getUuid(), player);
        this.sendToAll(PlayerListS2CPacket.entryFromPlayer(List.of(player)));
        this.sendWorldInfo(player, world);
        world.onPlayerConnected(player); //same as world.onRespawnPlayer or onTeleport...
        this.server.getBossBarManager().onPlayerConnect(player);

        for(var effect : player.getStatusEffects())
            serverPlayNetworkHandler.sendPacket(new EntityStatusEffectS2CPacket(player.getId(), effect));

        player.onSpawn();
        //may/must restore vehicle here, concurrently vehicle is not restored, and we can say it's a bug
    }

    /*
    The two players must have the same network handler since they must be linked to the same client, so network handler,  the id will be copied from the old player
     */
    public ServerPlayerEntity replaceBy(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer)
    {
        assert oldPlayer != newPlayer; //the two players must be different
        assert oldPlayer.getGameProfile() == newPlayer.getGameProfile(); //the two players must have the same profile

        this.players.remove(oldPlayer); //disable the old player
        var world = oldPlayer.getWorld();
        world.removePlayer(oldPlayer, CHANGED_DIMENSION);
        world.sendEntityStatus(oldPlayer, (byte)3);

        if(newPlayer.isRemoved())
        {
            PlayerEntityTest.logger.warn("The new player is removed, this may cause bugs");
            ((EntityAccessor)newPlayer).unsetRemovedMixin(); //unset the removed flag to reuse an old serverPlayer instance
        }


        var handler = oldPlayer.networkHandler;
        handler.player = newPlayer; //change the player in the network handler
        newPlayer.networkHandler = handler; //copy the network handler




        newPlayer.setId(oldPlayer.getId()); //copy the id
        newPlayer.setMainArm(oldPlayer.getMainArm()); //copy the main arm

        AddAndSendDefaultJoinPackets(newPlayer, false);
        return newPlayer;
    }
}
