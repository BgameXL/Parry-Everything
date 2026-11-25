package dev.bgame.parry;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bgame.ParryEverything;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ParryEverything.MODID, value = Dist.CLIENT)
final class ParryInput {
    private static final Lazy<KeyMapping> PARRY_KEY = Lazy.of(() -> new KeyMapping(
            "key.parry_everything.parry", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.parry_everything"));
    private static boolean keyHeld;

    @SubscribeEvent
    static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(PARRY_KEY.get());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        KeyMapping key = PARRY_KEY.get();
        boolean canSend = !keyHeld && minecraft.screen == null && minecraft.player != null && minecraft.player.isAlive();
        keyHeld = key.isDown();

        while (key.consumeClick()) {
            if (canSend) {
                PacketDistributor.sendToServer(ParryAttempt.INSTANCE);
                canSend = false;
            }
        }
    }
}