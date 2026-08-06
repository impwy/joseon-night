package kr.joseonnight.desktop.view;

final class DecorationLayout {
    static final int CELL_SIZE = 192;
    static final double DRAW_SIZE = 64.0;
    private static final int DENSITY_PERCENT = 30;
    private static final long SEED = 0x4A4F53454F4E4E4CL;

    private DecorationLayout() {
    }

    static Placement placementAt(int cellX, int cellY) {
        long hash = mix(SEED
                ^ (long) cellX * 0x9E3779B97F4A7C15L
                ^ (long) cellY * 0xC2B2AE3D27D4EB4FL);
        if (Math.floorMod(hash, 100L) >= DENSITY_PERCENT) {
            return null;
        }

        long xHash = mix(hash ^ 0x165667B19E3779F9L);
        long yHash = mix(hash ^ 0x85EBCA77C2B2AE63L);
        double worldX = (double) cellX * CELL_SIZE + 32.0 + Math.floorMod(xHash, 129L);
        double worldY = (double) cellY * CELL_SIZE + 32.0 + Math.floorMod(yHash, 129L);
        Kind kind = Kind.values()[(int) Math.floorMod(mix(hash ^ 0x27D4EB2F165667C5L), Kind.values().length)];
        return new Placement(worldX, worldY, kind);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    enum Kind {
        DRY_GRASS,
        RUBBLE,
        GROUND_CRACK
    }

    record Placement(double worldX, double worldY, Kind kind) {
    }
}
