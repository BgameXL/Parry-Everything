package dev.bgame.parry;

import dev.bgame.ParryEverything;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

record FeedbackPayload(boolean perfect) implements CustomPacketPayload {
    static final Type<FeedbackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ParryEverything.MODID, "parry_feedback"));
    static final StreamCodec<RegistryFriendlyByteBuf, FeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, FeedbackPayload::perfect, FeedbackPayload::new);

    @Override
    public Type<FeedbackPayload> type() {
        return TYPE;
    }
}
