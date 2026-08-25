package io.github.andrewwwwwwwwwwwwwww.ssc.corpse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a body comes to rest — ported from Fallen's CorpseEntity, minus the
 * animated fall: with no real body entity to tick, the final resting spot is
 * computed once at death. A still pool floats the body on its surface, flowing
 * fluid diverts to the nearest open surface, the void holds it just inside the
 * world, and plain air drops it straight onto the ground below.
 */
public final class CorpsePlacement {
    private CorpsePlacement() {}

    /** A body's resting spot; {@code pin} marks hazard placements (floating/void-held). */
    public record RestSpot(double x, double y, double z, boolean pin) {}

    /** True when there's no solid ground within {@code depth} blocks below the death spot. */
    public static boolean isOverVoid(Level level, BlockPos pos, int depth) {
        int bottom = Math.max(level.getMinY(), pos.getY() - Math.max(1, depth));
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int y = pos.getY() - 1; y >= bottom; y--) {
            cursor.setY(y);
            if (!level.getBlockState(cursor).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Scans straight down from the death spot; the first thing met decides:
     * still pool → float on its surface; solid → fall to it; flowing fluid →
     * nearest open surface; nothing to the world floor → held just inside.
     */
    public static RestSpot computeRestSpot(Level level, Vec3 deathPos) {
        BlockPos death = BlockPos.containing(deathPos);
        int x = death.getX();
        int z = death.getZ();
        int minY = level.getMinY();
        for (int y = death.getY(); y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            FluidState fluid = level.getFluidState(pos);
            if (fluid.isSource()) {
                return new RestSpot(deathPos.x, fluidSurfaceY(level, x, z, y), deathPos.z, true);
            }
            if (!fluid.isEmpty()) {
                RestSpot near = nearestOpenRest(level, deathPos, death);
                if (near != null) {
                    return near;
                }
                return new RestSpot(deathPos.x, deathPos.y, deathPos.z, true);
            }
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return new RestSpot(deathPos.x, deathPos.y, deathPos.z, false); // solid below — settle onto it
            }
        }
        return new RestSpot(deathPos.x, minY + 1, deathPos.z, true); // open void — hold it just inside the world
    }

    /** True when the body's feet (or the block above) sit in a <em>still</em> lava/water pool. */
    public static boolean inSourceFluid(Level level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y, z);
        return level.getFluidState(feet).isSource() || level.getFluidState(feet.above()).isSource();
    }

    /** Top of the still-fluid column at (x, z), so a body deep in a lake surfaces. */
    public static double fluidSurfaceY(Level level, int x, int z, int startY) {
        int top = startY;
        for (int cy = startY; cy <= level.getMaxY(); cy++) {
            if (!level.getFluidState(new BlockPos(x, cy, z)).isSource()) {
                break;
            }
            top = cy;
        }
        BlockPos topPos = new BlockPos(x, top, z);
        return top + level.getFluidState(topPos).getHeight(level, topPos);
    }

    /**
     * Shape-aware top surface of the first solid block at or below (x, y, z),
     * or null when there is nothing but air down to the world floor (the void).
     */
    public static Double surfaceBelowOrNull(Level level, double xd, double yd, double zd) {
        int x = Mth.floor(xd);
        int z = Mth.floor(zd);
        int minY = level.getMinY();
        for (int y = Mth.floor(yd + 0.5); y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return y + shape.max(Direction.Axis.Y);
            }
        }
        return null;
    }

    /** Nearest open rest, close pass then wide pass (reaches the floor of a tall fall). */
    private static RestSpot nearestOpenRest(Level level, Vec3 from, BlockPos center) {
        RestSpot near = nearestRest(level, from, center, 6, 2, 12);
        if (near != null) {
            return near;
        }
        return nearestRest(level, from, center, 10, 4, 24);
    }

    /** The solid-top-in-air or still-pool surface closest to {@code from}, or null. */
    private static RestSpot nearestRest(Level level, Vec3 from, BlockPos center,
                                        int radius, int up, int down) {
        RestSpot best = null;
        double bestDistSq = Double.MAX_VALUE;
        int minY = Math.max(level.getMinY(), center.getY() - down);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                for (int y = center.getY() + up; y >= minY; y--) {
                    BlockPos pos = new BlockPos(cx, y, cz);
                    FluidState fluid = level.getFluidState(pos);
                    if (fluid.isSource() && level.getFluidState(pos.above()).isEmpty()) {
                        double sy = y + fluid.getHeight(level, pos);
                        double d = from.distanceToSqr(cx + 0.5, sy, cz + 0.5);
                        if (d < bestDistSq) {
                            bestDistSq = d;
                            best = new RestSpot(cx + 0.5, sy, cz + 0.5, true);
                        }
                        break;
                    }
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        BlockPos above = pos.above();
                        if (level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                                && level.getFluidState(above).isEmpty()) {
                            double sy = y + shape.max(Direction.Axis.Y);
                            double d = from.distanceToSqr(cx + 0.5, sy, cz + 0.5);
                            if (d < bestDistSq) {
                                bestDistSq = d;
                                best = new RestSpot(cx + 0.5, sy, cz + 0.5, false);
                            }
                        }
                        break;
                    }
                }
            }
        }
        return best;
    }
}
