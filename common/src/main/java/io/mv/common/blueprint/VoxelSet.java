package io.mv.common.blueprint;

import io.mv.common.math.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Connected voxels of a vehicle structure (MV-SPEC 6.2). Pure data: no MC BlockState,
 * just relative integer coordinates + density + functional tags. BFS assembly lives
 * server-side; this class performs 6-neighbor connectivity filtering and can be unit-tested.
 */
public final class VoxelSet {
    public record Voxel(int x, int y, int z, double density, Set<String> tags,
                        boolean collidable, boolean connector, boolean noAssemble) {
        public boolean hasTag(String tag) { return tags.contains(tag); }
    }

    private final List<Voxel> voxels = new ArrayList<>();

    public VoxelSet() {}

    /** 6-neighbor relative offsets. */
    private static final int[][] NEIGHBORS = {
            {1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}
    };

    /**
     * BFS flood-fill from a root voxel over a provided coordinate->voxel map.
     * Only connector-tagged voxels may bridge across a gap larger than 1 (handled by
     * the map keys already encoding adjacency). noAssemble voxels form hard boundaries.
     *
     * @param all  all candidate voxels keyed by packed coordinate
     * @param root root coordinate
     * @param maxVoxels assembly cap
     * @return connected set, or null if cap exceeded
     */
    public static VoxelSet assemble(java.util.Map<Long, Voxel> all, Vec3 root, int maxVoxels) {
        VoxelSet out = new VoxelSet();
        long rootKey = key((int) root.x(), (int) root.y(), (int) root.z());
        if (!all.containsKey(rootKey)) return out;

        Set<Long> visited = new HashSet<>();
        List<long[]> stack = new ArrayList<>();
        stack.add(new long[]{(int) root.x(), (int) root.y(), (int) root.z()});
        visited.add(rootKey);

        while (!stack.isEmpty()) {
            long[] cur = stack.remove(stack.size() - 1);
            int x = (int) cur[0], y = (int) cur[1], z = (int) cur[2];
            Voxel v = all.get(key(x, y, z));
            if (v == null || v.noAssemble()) continue;
            out.voxels.add(v);
            if (out.voxels.size() > maxVoxels) return null; // cap exceeded -> assembly fails

            for (int[] d : NEIGHBORS) {
                long nk = key(x + d[0], y + d[1], z + d[2]);
                if (!visited.contains(nk) && all.containsKey(nk)) {
                    visited.add(nk);
                    stack.add(new long[]{x + d[0], y + d[1], z + d[2]});
                }
            }
        }
        return out;
    }

    public void add(Voxel v) { voxels.add(v); }

    public List<Voxel> voxels() { return List.copyOf(voxels); }
    public int size() { return voxels.size(); }

    /** Pack 3 ints into a long key. */
    public static long key(int x, int y, int z) {
        return (((long) x & 0xFFFFFFL) << 40) | (((long) y & 0xFFFFL) << 24) | ((long) z & 0xFFFFL);
    }
}
