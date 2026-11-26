package dev.bgame.parry;

import dev.bgame.ParryEverything;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

record ArrowWarning(int arrowId, int ticksUntilImpact) implements CustomPacketPayload {
    static final Type<ArrowWarning> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ParryEverything.MODID, "arrow_warning"));
    static final StreamCodec<RegistryFriendlyByteBuf, ArrowWarning> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArrowWarning::arrowId,
            ByteBufCodecs.VAR_INT, ArrowWarning::ticksUntilImpact,
            ArrowWarning::new);

    @Override
    public Type<ArrowWarning> type() {
        return TYPE;
    }
}