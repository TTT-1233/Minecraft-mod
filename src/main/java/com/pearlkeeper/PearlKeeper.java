package com.pearlkeeper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Freezes a player's in-flight ender pearls when they disconnect and puts them
 * back, at the same position and velocity, shortly after they rejoin.
 *
 * <p>Runs entirely on the logical server: dedicated servers, LAN hosts and
 * singleplayer all work, and clients do not need the mod installed.
 */
public class PearlKeeper implements ModInitializer {
    public static final String MOD_ID = "pearlkeeper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final List<PendingRestore> pendingRestores = new ArrayList<>();
    private PearlKeeperConfig config;
    private PearlStorage storage;

    @Override
    public void onInitialize() {
        config = PearlKeeperConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            storage = new PearlStorage(server.getSavePath(WorldSavePath.ROOT).resolve(MOD_ID));
            pendingRestores.clear();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            storage = null;
            pendingRestores.clear();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.player, server));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player, server));
        ServerTickEvents.END_SERVER_TICK.register(this::tickPendingRestores);
    }

    /**
     * Fires before the player is removed from the world, so their pearls are
     * still present and can be taken out before vanilla gets to them.
     */
    private void onDisconnect(ServerPlayerEntity player, MinecraftServer server) {
        if (!config.enabled || storage == null) {
            return;
        }

        UUID playerId = player.getUuid();
        List<StoredPearl> pearls = new ArrayList<>();

        // A restore that never got to run still owns the pearls in the file.
        if (pendingRestores.removeIf(pending -> pending.playerId.equals(playerId))) {
            pearls.addAll(storage.load(playerId));
        }

        for (ServerWorld world : server.getWorlds()) {
            for (EnderPearlEntity pearl : world.getEntitiesByType(EntityType.ENDER_PEARL, p -> p.getOwner() == player)) {
                Vec3d velocity = pearl.getVelocity();
                pearls.add(new StoredPearl(
                        world.getRegistryKey().getValue().toString(),
                        pearl.getX(), pearl.getY(), pearl.getZ(),
                        velocity.x, velocity.y, velocity.z,
                        pearl.getYaw(), pearl.getPitch(),
                        System.currentTimeMillis()
                ));
                pearl.discard();
            }
        }

        if (pearls.size() > config.maxPearlsPerPlayer) {
            pearls = pearls.subList(0, config.maxPearlsPerPlayer);
        }

        storage.save(playerId, pearls);

        if (!pearls.isEmpty()) {
            LOGGER.info("Froze {} ender pearl(s) for {}", pearls.size(), player.getGameProfile().getName());
        }
    }

    private void onJoin(ServerPlayerEntity player, MinecraftServer server) {
        if (!config.enabled || storage == null) {
            return;
        }

        if (config.restoreDelayTicks <= 0) {
            restore(player.getUuid(), server);
        } else {
            pendingRestores.add(new PendingRestore(player.getUuid(), config.restoreDelayTicks));
        }
    }

    private void tickPendingRestores(MinecraftServer server) {
        if (pendingRestores.isEmpty()) {
            return;
        }

        Iterator<PendingRestore> iterator = pendingRestores.iterator();
        List<UUID> ready = new ArrayList<>();

        while (iterator.hasNext()) {
            PendingRestore pending = iterator.next();
            if (--pending.ticksRemaining <= 0) {
                iterator.remove();
                ready.add(pending.playerId);
            }
        }

        for (UUID playerId : ready) {
            restore(playerId, server);
        }
    }

    private void restore(UUID playerId, MinecraftServer server) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            // Left again before the delay elapsed; the pearls stay on disk.
            return;
        }

        List<StoredPearl> pearls = storage.load(playerId);
        storage.clear(playerId);

        long expiryMillis = (long) (config.maxOfflineHours * 3600_000.0);
        int restored = 0;

        for (StoredPearl stored : pearls) {
            if (expiryMillis > 0 && System.currentTimeMillis() - stored.savedAtEpochMillis() > expiryMillis) {
                continue;
            }
            if (spawn(stored, player, server)) {
                restored++;
            }
        }

        if (restored > 0) {
            LOGGER.info("Restored {} ender pearl(s) for {}", restored, player.getGameProfile().getName());
            if (config.notifyPlayer) {
                player.sendMessage(Text.literal("Resumed " + restored + " ender pearl" + (restored == 1 ? "" : "s")
                        + " from where you left off."), false);
            }
        }
    }

    private boolean spawn(StoredPearl stored, ServerPlayerEntity owner, MinecraftServer server) {
        Identifier dimensionId = Identifier.tryParse(stored.dimension());
        if (dimensionId == null) {
            return false;
        }

        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimensionId));
        if (world == null) {
            LOGGER.warn("Dropping a stored pearl: dimension {} no longer exists", stored.dimension());
            return false;
        }

        EnderPearlEntity pearl = EntityType.ENDER_PEARL.create(world, SpawnReason.TRIGGERED);
        if (pearl == null) {
            return false;
        }

        // The pearl only ticks once its chunk is loaded.
        world.getChunk(MathHelper.floor(stored.x()) >> 4, MathHelper.floor(stored.z()) >> 4);

        pearl.setOwner(owner);
        pearl.refreshPositionAndAngles(stored.x(), stored.y(), stored.z(), stored.yaw(), stored.pitch());
        pearl.setVelocity(stored.velocityX(), stored.velocityY(), stored.velocityZ());
        pearl.velocityModified = true;

        return world.spawnEntity(pearl);
    }

    private static final class PendingRestore {
        private final UUID playerId;
        private int ticksRemaining;

        private PendingRestore(UUID playerId, int ticksRemaining) {
            this.playerId = playerId;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
