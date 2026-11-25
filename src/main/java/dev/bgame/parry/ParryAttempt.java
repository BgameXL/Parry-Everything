package dev.bgame.parry;

import dev.bgame.ParryEverything;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

record ParryAttempt() implements CustomPacketPayload {
    static final ParryAttempt INSTANCE = new ParryAttempt();
    static final Type<ParryAttempt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ParryEverything.MODID, "parry_attempt"));
    static final StreamCodec<RegistryFriendlyByteBuf, ParryAttempt> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ParryAttempt> type() {
        return TYPE;
    }
}