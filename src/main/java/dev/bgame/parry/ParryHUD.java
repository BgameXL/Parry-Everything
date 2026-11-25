package dev.bgame.parry;

import com.mojang.math.Axis;
import dev.bgame.ParryEverything;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = ParryEverything.MODID, value = Dist.CLIENT)
final class ParryHUD {
    private static final long HIT_DISPLAY_MILLIS = 350;
    private static final int WARNING_MILLIS = Parry.MELEE_WINDUP_TICKS * SharedConstants.MILLIS_PER_TICK;
    private static final Map<Integer, Long> MELEE_WARNINGS = new HashMap<>();
    private static long hitVisibleUntil;
    private static boolean perfect;

    static void showHit(boolean perfectParry) {
        perfect = perfectParry;
        hitVisibleUntil = Util.getMillis() + HIT_DISPLAY_MILLIS;
    }

    static void updateMeleeWarning(int attackerId, boolean active) {
        if (active) {
            MELEE_WARNINGS.put(attackerId, Util.getMillis() + WARNING_MILLIS);
        } else {
            MELEE_WARNINGS.remove(attackerId);
        }
    }

    @SubscribeEvent
    static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(ParryEverything.MODID, "parry_hit_indicator"),
                ParryHUD::render);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hitVisibleUntil = 0;
        MELEE_WARNINGS.clear();
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        long now = Util.getMillis();
        boolean showHit = hitVisibleUntil > now;
        if (!showHit && MELEE_WARNINGS.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        renderMeleeWarning(graphics, now);
        if (showHit) {
            renderHit(graphics);
        }
    }

    private static void renderMeleeWarning(GuiGraphics graphics, long now) {
        long nextAttack = Long.MAX_VALUE;
        for (var warnings = MELEE_WARNINGS.values().iterator(); warnings.hasNext(); ) {
            long expiresAt = warnings.next();
            if (expiresAt <= now) {
                warnings.remove();
            } else {
                nextAttack = Math.min(nextAttack, expiresAt);
            }
        }
        if (nextAttack == Long.MAX_VALUE) {
            return;
        }

        int width = 26;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 - 16;
        float progress = 1f - (nextAttack - now) / (float) WARNING_MILLIS;
        int filled = Math.max(1, Math.round(width * progress));
        graphics.fill(x - 1, y - 1, x + width + 1, y + 3, 0xD0121011);
        graphics.fill(x, y, x + width, y + 2, 0xFF742C35);
        graphics.fill(x, y, x + filled, y + 2, 0xFFFF635C);
    }

    private static void renderHit(GuiGraphics graphics) {
        int reach = perfect ? 14 : 11;
        int halfWidth = perfect ? 2 : 1;
        int color = perfect ? 0xFFFFD46B : 0xFFF4F7FF;
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2f, graphics.guiHeight() / 2f, 0f);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45f));
        drawArms(graphics, 5, reach + 1, halfWidth + 1, 0xB0000000);
        drawArms(graphics, 6, reach, halfWidth, color);
        graphics.pose().popPose();
    }

    private static void drawArms(GuiGraphics graphics, int gap, int reach, int halfWidth, int color) {
        graphics.fill(-reach, -halfWidth, -gap, halfWidth, color);
        graphics.fill(gap, -halfWidth, reach, halfWidth, color);
        graphics.fill(-halfWidth, -reach, halfWidth, -gap, color);
        graphics.fill(-halfWidth, gap, halfWidth, reach, color);
    }
}
