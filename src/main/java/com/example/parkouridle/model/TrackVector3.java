package com.example.parkouridle.model;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

import java.util.Objects;

public final class TrackVector3 {
    public int x;
    public int y;
    public int z;

    public TrackVector3() {
    }

    public TrackVector3(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static TrackVector3 fromVector3i(Vector3i vec) {
        return new TrackVector3(vec.getX(), vec.getY(), vec.getZ());
    }

    public Vector3d toCenterVector3d() {
        return new Vector3d(x + 0.5, y + 1.0, z + 0.5);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TrackVector3 that)) {
            return false;
        }
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
