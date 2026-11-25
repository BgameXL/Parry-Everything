package dev.bgame.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.bgame.parry.Parry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MeleeAttackGoal.class)
abstract class MeleeGoal {
    @Shadow @Final protected PathfinderMob mob;
    @Shadow @Final private boolean followingTargetEvenIfNotSeen;

    @Unique private ServerPlayer parryEverything$warnedPlayer;
    @Unique private long parryEverything$attackAt;

    @ModifyExpressionValue(
            method = "checkAndPerformAttack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/MeleeAttackGoal;canPerformAttack(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean parryEverything$windUpAttack(boolean canAttack, LivingEntity target) {
        long now = mob.level().getGameTime();
        if (parryEverything$warnedPlayer != null) {
            if (!canAttack || target != parryEverything$warnedPlayer) {
                parryEverything$clearWarning();
            } else if (now < parryEverything$attackAt) {
                return false;
            } else {
                parryEverything$clearWarning();
                return true;
            }
        }

        if (canAttack && target instanceof ServerPlayer player && !(mob instanceof Creeper)
                && Parry.sendMeleeWarning(player, mob.getId(), true)) {
            parryEverything$warnedPlayer = player;
            parryEverything$attackAt = now + Parry.MELEE_WINDUP_TICKS;
            return false;
        }

        return canAttack;
    }

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void parryEverything$continueWindup(CallbackInfoReturnable<Boolean> result) {
        // These goals normally stop as soon as navigation reaches the target.
        ServerPlayer player = parryEverything$warnedPlayer;
        if (!result.getReturnValueZ() && !followingTargetEvenIfNotSeen && player != null
                && player == mob.getTarget() && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && mob.isWithinMeleeAttackRange(player) && mob.getSensing().hasLineOfSight(player)) {
            result.setReturnValue(true);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void parryEverything$cancelWindup(CallbackInfo callback) {
        parryEverything$clearWarning();
    }

    @Unique
    private void parryEverything$clearWarning() {
        if (parryEverything$warnedPlayer != null) {
            Parry.sendMeleeWarning(parryEverything$warnedPlayer, mob.getId(), false);
            parryEverything$warnedPlayer = null;
        }
    }
}
