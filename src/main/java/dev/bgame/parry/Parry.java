package dev.bgame.parry;

import dev.bgame.ParryEverything;
import dev.bgame.mixin.Projectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class Parry {
    public static final int MELEE_WINDUP_TICKS = 8;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ParryEverything.MODID);
    private static final Supplier<AttachmentType<ParryState>> STATE =
            ATTACHMENTS.register("parry_state", () -> AttachmentType.builder(ParryState::new).build());
    static final Supplier<AttachmentType<ArrowWarnings.Tracking>> ARROW_WARNING =
            ATTACHMENTS.register("arrow_warning", () -> AttachmentType.builder(ArrowWarnings.Tracking::new).build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Parry::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Parry::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(Parry::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(Parry::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(ArrowWarnings::onArrowTick);
        NeoForge.EVENT_BUS.addListener(ArrowWarnings::onArrowRemoved);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ParryAttempt.TYPE, ParryAttempt.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
                player.getData(STATE).tryStart(player.serverLevel().getGameTime());
            }
        });
        PayloadRegistrar client = registrar.optional();
        client.playToClient(FeedbackPayload.TYPE, FeedbackPayload.STREAM_CODEC,
                (payload, context) -> ParryHUD.showHit(payload.perfect()));
        client.playToClient(MeleeWarning.TYPE, MeleeWarning.STREAM_CODEC,
                (payload, context) -> ParryHUD.updateWarning(payload.attackerId(), payload.active() ? MELEE_WINDUP_TICKS : 0));
        client.playToClient(ArrowWarning.TYPE, ArrowWarning.STREAM_CODEC,
                (payload, context) -> ParryHUD.updateWarning(payload.arrowId(), payload.ticksUntilImpact()));
    }

    public static boolean sendMeleeWarning(ServerPlayer player, int attackerId, boolean active) {
        if (player.connection == null || !player.connection.hasChannel(MeleeWarning.TYPE)) {
            return false;
        }

        PacketDistributor.sendToPlayer(player, new MeleeWarning(attackerId, active));
        return true;
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0f) {
            return;
        }

        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof Mob attacker) || source.getEntity() != attacker
                || !(source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.STING))) {
            return;
        }

        ParryState.Result result = consumeParry(player);
        if (result == ParryState.Result.NONE) {
            return;
        }

        event.setCanceled(true);
        boolean perfect = result == ParryState.Result.PERFECT;
        if (perfect) {
            attacker.knockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        }
        playFeedback(player, perfect);
    }

    private static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)
                || !(event.getProjectile() instanceof AbstractArrow arrow) || !isParryableArrow(arrow)) {
            return;
        }

        ParryState.Result result = consumeParry(player);
        if (result == ParryState.Result.NONE) {
            return;
        }

        event.setCanceled(true);
        ArrowWarnings.clear(arrow);
        boolean perfect = result == ParryState.Result.PERFECT;
        if (perfect) {
            reflectArrow(arrow, player);
        } else {
            arrow.discard();
        }
        playFeedback(player, perfect);
    }

    static boolean isParryableArrow(AbstractArrow arrow) {
        return (arrow instanceof Arrow || arrow instanceof SpectralArrow) && arrow.getOwner() instanceof Mob;
    }

    private static ParryState.Result consumeParry(ServerPlayer player) {
        ParryState state = player.getExistingDataOrNull(STATE);
        return state == null ? ParryState.Result.NONE : state.consume(player.serverLevel().getGameTime());
    }

    private static void reflectArrow(AbstractArrow arrow, ServerPlayer player) {
        Vec3 reflectedVelocity = arrow.getDeltaMovement().scale(-1.0);
        AbstractArrow.Pickup pickup = arrow.pickup;
        arrow.deflect(ProjectileDeflection.REVERSE, player, player, true);
        arrow.setDeltaMovement(reflectedVelocity);
        arrow.pickup = pickup;
        ((Projectile) arrow).parryEverything$setLeftOwner(false);
    }

    private static void playFeedback(ServerPlayer player, boolean perfect) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                perfect ? SoundEvents.ANVIL_LAND : SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.0f);
        if (player.connection != null && player.connection.hasChannel(FeedbackPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new FeedbackPayload(perfect));
        }
    }

    private static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        event.getEntity().removeData(STATE);
    }
}
