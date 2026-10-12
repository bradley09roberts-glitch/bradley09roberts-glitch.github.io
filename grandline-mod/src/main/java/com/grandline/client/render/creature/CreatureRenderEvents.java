package com.grandline.client.render.creature;

import com.grandline.GrandLine;
import com.grandline.creature.BeastForms;
import com.grandline.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.GeckoLibCache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Client hooks for creatures: registers the Sea King renderer and swaps players/NPCs in a full Zoan form for their
 * GeckoLib beast model (falls back to the normal humanoid rendering when no model exists for the beast id).
 */
public final class CreatureRenderEvents {
    private static final Set<String> GLOWING = Set.of("phoenix", "seiryu");
    @Nullable private static EntityRendererProvider.Context context;
    private static final Map<String, Optional<BeastFormRenderer>> BEASTS = new HashMap<>();

    @Nullable
    public static BeastFormRenderer beastRenderer(String id) {
        if (context == null) return null;
        return BEASTS.computeIfAbsent(id, k -> {
            BeastForms.Beast b = BeastForms.get(k);
            ResourceLocation geo = BeastModel.geo(k);
            if (b == null || !GeckoLibCache.getBakedModels().containsKey(geo)) return Optional.empty();
            return Optional.of(new BeastFormRenderer(context, new BeastAnimatable(b), GLOWING.contains(k)));
        }).orElse(null);
    }

    @Nullable
    private static BeastFormRenderer rendererFor(LivingEntity e) {
        BeastForms.Beast b = BeastForms.of(e);
        return b == null ? null : beastRenderer(b.id());
    }

    @EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(ModEntities.SEA_KING.get(), SeaKingRenderer::new);
            ModEntities.WILDLIFE.forEach((k, t) -> e.registerEntityRenderer(t.get(), ctx -> new WildBeastRenderer(ctx, k)));
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers e) {
            context = e.getContext();
            BEASTS.clear();
        }
    }

    @EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
    public static final class Game {
        /** Highest priority so the humanoid pipeline (and its pose-stack push) never runs for a beast. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void renderLiving(RenderLivingEvent.Pre<?, ?> e) {
            LivingEntity le = e.getEntity();
            BeastFormRenderer r = rendererFor(le);
            if (r == null) return;
            e.setCanceled(true);
            float yaw = Mth.rotLerp(e.getPartialTick(), le.yRotO, le.getYRot());
            r.render(le, yaw, e.getPartialTick(), e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight());
        }

        /** Big beasts need the third-person camera further back. */
        @SubscribeEvent
        public static void cameraDistance(net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent e) {
            if (!(e.getCamera().getEntity() instanceof LivingEntity le)) return;
            BeastForms.Beast b = BeastForms.of(le);
            if (b != null && beastRenderer(b.id()) != null) e.setDistance(e.getDistance() * b.camera());
        }

        /** No human arm in first person while in a full beast form. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void hand(RenderHandEvent e) {
            var p = Minecraft.getInstance().player;
            if (p != null && rendererFor(p) != null) e.setCanceled(true);
        }
    }

    private CreatureRenderEvents() {}
}
