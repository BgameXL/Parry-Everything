package dev.bgame.parry;

import dev.bgame.ParryEverything;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

record MeleeWarning(int attackerId, boolean active) implements CustomPacketPayload {
    static final Type<MeleeWarning> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ParryEverything.MODID, "melee_warning"));
    static final StreamCodec<RegistryFriendlyByteBuf, MeleeWarning> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MeleeWarning::attackerId,
            ByteBufCodecs.BOOL, MeleeWarning::active,
            MeleeWarning::new);

    @Override
    public Type<MeleeWarning> type() {
        return TYPE;
    }
}
