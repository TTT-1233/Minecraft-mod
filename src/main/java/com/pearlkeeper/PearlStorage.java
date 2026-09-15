package com.pearlkeeper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Per-world storage of frozen pearls, one JSON file per player under
 * {@code <world>/pearlkeeper/}. Stored inside the world save so that a
 * backup or a world copy carries the pending pearls with it.
 */
public final class PearlStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;

    public PearlStorage(Path directory) {
        this.directory = directory;
    }

    public List<StoredPearl> load(UUID playerId) {
        Path file = fileFor(playerId);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<StoredPearl> pearls = GSON.fromJson(reader, new TypeToken<List<StoredPearl>>() {
            }.getType());
            return pearls == null ? List.of() : pearls;
        } catch (IOException | RuntimeException e) {
            PearlKeeper.LOGGER.error("Failed to read stored pearls for {}", playerId, e);
            return List.of();
        }
    }

    public void save(UUID playerId, List<StoredPearl> pearls) {
        if (pearls.isEmpty()) {
            clear(playerId);
            return;
        }

        try {
            Files.createDirectories(directory);
            try (Writer writer = Files.newBufferedWriter(fileFor(playerId), StandardCharsets.UTF_8)) {
                GSON.toJson(pearls, writer);
            }
        } catch (IOException e) {
            PearlKeeper.LOGGER.error("Failed to write stored pearls for {}", playerId, e);
        }
    }

    public void clear(UUID playerId) {
        try {
            Files.deleteIfExists(fileFor(playerId));
        } catch (IOException e) {
            PearlKeeper.LOGGER.error("Failed to delete stored pearls for {}", playerId, e);
        }
    }

    private Path fileFor(UUID playerId) {
        return directory.resolve(playerId + ".json");
    }
}
