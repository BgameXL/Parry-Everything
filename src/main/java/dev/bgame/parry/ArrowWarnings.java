package dev.bgame.parry;

import dev.bgame.mixin.ArrowFlight;
import dev.bgame.mixin.Projectile;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class ArrowWarnings {
    static final int LOOKAHEAD_TICKS = 8;

    static void onArrowTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }

        if (!Parry.isParryableArrow(arrow) || arrow.isRemoved() || arrow.isNoPhysics()
                || ((ArrowFlight) arrow).parryEverything$isInGround()) {
            clear(arrow);
            return;
        }

        Impact impact = predictImpact(arrow, level);
        if (impact == null) {
            clear(arrow);
            return;
        }

        Tracking tracking = arrow.getData(Parry.ARROW_WARNING);
        long impactAt = level.getGameTime() + impact.ticks();
        if (tracking.target == impact.player() && tracking.impactAt == impactAt) {
            return;
        }
        if (tracking.target != null && tracking.target != impact.player()) {
            send(tracking.target, arrow, 0);
        }

        tracking.target = impact.player();
        tracking.impactAt = impactAt;
        send(impact.player(), arrow, impact.ticks());
    }

    static void onArrowRemoved(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof AbstractArrow arrow) {
            clear(arrow);
        }
    }

    static void clear(AbstractArrow arrow) {
        Tracking tracking = arrow.removeData(Parry.ARROW_WARNING);
        if (tracking != null && tracking.target != null) {
            send(tracking.target, arrow, 0);
        }
    }

    private static Impact predictImpact(AbstractArrow arrow, ServerLevel level) {
        Vec3 origin = arrow.position();
        Vec3 motion = arrow.getDeltaMovement();
        double gravity = arrow.getGravity();
        double reach = (motion.length() + gravity * LOOKAHEAD_TICKS) * LOOKAHEAD_TICKS + 1.0;
        if (level.getEntitiesOfClass(ServerPlayer.class, arrow.getBoundingBox().inflate(reach), ArrowWarnings::canWarn).isEmpty()) {
            return null;
        }

        Vec3 start = origin;
        float drag = arrow.isInWater() ? ((ArrowFlight) arrow).parryEverything$getWaterInertia() : 0.99F;
        Projectile projectile = (Projectile) arrow;
        for (int ticks = 1; ticks <= LOOKAHEAD_TICKS; ticks++) {
            Vec3 end = start.add(motion);
            if (!hasLoadedPath(level, start, end)) {
                return null;
            }

            BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow));
            AABB search = arrow.getBoundingBox().move(start.subtract(origin)).expandTowards(motion).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                    level, arrow, start, block.getLocation(), search, projectile::parryEverything$canHitEntity);
            if (hit != null) {
                return hit.getEntity() instanceof ServerPlayer player && canWarn(player) ? new Impact(player, ticks) : null;
            }
            if (block.getType() != HitResult.Type.MISS) {
                return null;
            }

            start = end;
            motion = motion.scale(drag).add(0.0, -gravity, 0.0);
        }

        return null;
    }

    private static boolean hasLoadedPath(ServerLevel level, Vec3 start, Vec3 end) {
        int minX = SectionPos.posToSectionCoord(Math.min(start.x, end.x));
        int maxX = SectionPos.posToSectionCoord(Math.max(start.x, end.x));
        int minZ = SectionPos.posToSectionCoord(Math.min(start.z, end.z));
        int maxZ = SectionPos.posToSectionCoord(Math.max(start.z, end.z));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canWarn(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.connection != null && player.connection.hasChannel(ArrowWarning.TYPE);
    }

    private static void send(ServerPlayer player, AbstractArrow arrow, int ticksUntilImpact) {
        if (player.connection != null && player.connection.hasChannel(ArrowWarning.TYPE)) {
            PacketDistributor.sendToPlayer(player, new ArrowWarning(arrow.getId(), ticksUntilImpact));
        }
    }

    static final class Tracking {
        private ServerPlayer target;
        private long impactAt;
    }

    private record Impact(ServerPlayer player, int ticks) {
    }
}