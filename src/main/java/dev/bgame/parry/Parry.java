package dev.bgame.parry;

import dev.bgame.ParryEverything;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
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

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Parry::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Parry::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(Parry::onDimensionChange);
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
                (payload, context) -> ParryHUD.updateMeleeWarning(payload.attackerId(), payload.active()));
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

        ParryState state = player.getExistingDataOrNull(STATE);
        if (state == null) {
            return;
        }

        ParryState.Result result = state.consume(player.serverLevel().getGameTime());
        if (result == ParryState.Result.NONE) {
            return;
        }

        event.setCanceled(true);
        boolean perfect = result == ParryState.Result.PERFECT;
        if (perfect) {
            attacker.knockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        }

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
