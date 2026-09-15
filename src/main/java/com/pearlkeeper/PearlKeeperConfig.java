package com.pearlkeeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PearlKeeperConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Master switch. When false the mod leaves pearls to vanilla behaviour. */
    public boolean enabled = true;

    /** How many in-flight pearls per player are kept. Extra pearls are dropped. */
    public int maxPearlsPerPlayer = 8;

    /** Discard pearls after this many hours offline. 0 disables the expiry. */
    public double maxOfflineHours = 0.0;

    /** Delay before pearls are put back, so the player's chunks are loaded first. */
    public int restoreDelayTicks = 20;

    /** Tell the player in chat how many pearls were restored. */
    public boolean notifyPlayer = true;

    public static PearlKeeperConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            PearlKeeperConfig config = new PearlKeeperConfig();
            config.save(file);
            return config;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PearlKeeperConfig config = GSON.fromJson(reader, PearlKeeperConfig.class);
            return config == null ? new PearlKeeperConfig() : config;
        } catch (IOException | RuntimeException e) {
            PearlKeeper.LOGGER.error("Failed to read {}, using defaults", file, e);
            return new PearlKeeperConfig();
        }
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            PearlKeeper.LOGGER.error("Failed to write {}", file, e);
        }
    }
}
