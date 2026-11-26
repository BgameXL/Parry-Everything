package dev.bgame.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(net.minecraft.world.entity.projectile.Projectile.class)
public interface Projectile {
    @Accessor("leftOwner")
    void parryEverything$setLeftOwner(boolean leftOwner);

    @Invoker("canHitEntity")
    boolean parryEverything$canHitEntity(Entity entity);
}