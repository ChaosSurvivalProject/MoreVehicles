package io.mv.server.rail;

import io.mv.common.math.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Rail graph (MV-SPEC 5.1): nodes/edges with tangent, curvature kappa=1/R, slope theta,
 * cant e, switch state. Curvature/slope use finite-difference smoothing to avoid jumps.
 * Server-side; does not touch vanilla Minecart entity classes.
 */
public final class RailGraph {
    public record RailNode(Vec3 pos, Vec3 tangent, double curvature, double slope,
                           double cant, boolean switchState, boolean powered) {}

    private final Map<Integer, RailNode> nodes = new HashMap<>();

    public void clear() { nodes.clear(); }

    public void add(int id, RailNode node) { nodes.put(id, node); }

    public RailNode get(int id) { return nodes.get(id); }

    public int size() { return nodes.size(); }

    /** Query track geometry at a world position (nearest node; simplified for skeleton). */
    public RailNode sample(Vec3 worldPos) {
        RailNode best = null;
        double bestD = Double.MAX_VALUE;
        for (RailNode n : nodes.values()) {
            double d = n.pos().sub(worldPos).lengthSq();
            if (d < bestD) { bestD = d; best = n; }
        }
        return best;
    }
}
