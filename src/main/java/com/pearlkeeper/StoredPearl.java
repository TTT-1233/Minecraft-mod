package com.pearlkeeper;

/**
 * A single ender pearl frozen at the moment its owner disconnected.
 */
public record StoredPearl(
        String dimension,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        float yaw,
        float pitch,
        long savedAtEpochMillis
) {
}
