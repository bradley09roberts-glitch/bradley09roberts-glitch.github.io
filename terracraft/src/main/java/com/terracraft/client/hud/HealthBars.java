package com.terracraft.client.hud;

import com.terracraft.TerraCraft;
import com.terracraft.config.TerraConfig;
import com.terracraft.entity.boss.TerrariaBoss;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * TerraCraft's health bars.
 * <ul>
 *     <li><b>Boss bars</b> (bosses and invasions): a framed, gold-cornered bar in the boss's colour with the boss's
 *     real life ("12,340 / 18,000") inside, a pale trail showing recent damage and notches every 10%.</li>
 *     <li><b>Focus bar</b>: look at any creature within 48 blocks and a slim panel at the top of the screen shows its
 *     name and life, fading out a couple of seconds after you look away. It is colored from green to red with its
 *     life, flashes when the creature is hit and leaves the same damage trail.</li>
 * </ul>
 * Life is the creature's real health (bosses have their full Terraria life; see {@code TerrariaMob.raiseHealthCap}).
 */
public final class HealthBars {
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.ROOT);
    private static final double FOCUS_RANGE = 48.0;
    private static final int FOCUS_LINGER = 50;

    /** Lagging "recent damage" fraction and last seen life per bar (boss events by UUID, the focus bar by entity). */
    private static final Map<Object, Trail> TRAILS = new HashMap<>();
    private static int focusId = -1;
    private static int focusTicks;
    private static int bossBarsBottom;

    private record Palette(int light, int dark) {}

    private static final class Trail {
        float lag = 1.0F;
        float last = 1.0F;
        int hold;
        int flash;
    }

    private HealthBars() {}

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, TerraCraft.id("focus_health"),
            HealthBars::renderFocus));
        NeoForge.EVENT_BUS.addListener(HealthBars::onBossBar);
        NeoForge.EVENT_BUS.addListener(HealthBars::onClientTick);
    }

    // ---------------------------------------------------------------- state

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        LivingEntity looked = lookedAt(mc);
        if (looked != null) {
            if (looked.getId() != focusId) {
                TRAILS.remove(focusId);
            }
            focusId = looked.getId();
            focusTicks = FOCUS_LINGER;
        } else if (focusTicks > 0) {
            focusTicks--;
        }
        for (Trail trail : TRAILS.values()) {
            if (trail.hold > 0) {
                trail.hold--;
            }
            if (trail.flash > 0) {
                trail.flash--;
            }
        }
    }

    /** The living creature under the crosshair within 48 blocks, unless a block is in the way. */
    private static @Nullable LivingEntity lookedAt(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            return null;
        }
        Vec3 from = camera.getEyePosition();
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 to = from.add(look.scale(FOCUS_RANGE));
        AABB area = camera.getBoundingBox().expandTowards(look.scale(FOCUS_RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, from, to, area,
            e -> e instanceof LivingEntity living && living.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand) && !e.isInvisible(),
            FOCUS_RANGE * FOCUS_RANGE);
        if (hit == null) {
            return null;
        }
        HitResult block = mc.level.clip(new ClipContext(from, hit.getLocation(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
        if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceToSqr(from) < hit.getLocation().distanceToSqr(from) - 0.25) {
            return null;
        }
        return (LivingEntity) hit.getEntity();
    }

    private static Trail trail(Object key, float fraction) {
        Trail trail = TRAILS.computeIfAbsent(key, k -> {
            Trail t = new Trail();
            t.lag = fraction;
            t.last = fraction;
            return t;
        });
        if (fraction < trail.last - 1.0E-4F) {
            trail.hold = 12;      // keep the damage chunk visible for a moment, then let it drain
            trail.flash = 4;
        }
        if (fraction > trail.lag) {
            trail.lag = fraction;   // healed
        } else if (trail.hold <= 0) {
            trail.lag = Math.max(fraction, trail.lag - 0.006F);
        }
        trail.last = fraction;
        return trail;
    }

    // ---------------------------------------------------------------- boss bars

    private static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!enabled(TerraConfig.CLIENT.customBossBars)) {
            return;
        }
        event.setCanceled(true);
        event.setIncrement(28);
        LerpingBossEvent boss = event.getBossEvent();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int width = 220;
        int x = graphics.guiWidth() / 2 - width / 2;
        int y = event.getY();
        Palette palette = palette(boss.getColor());
        float progress = Mth.clamp(boss.getProgress(), 0.0F, 1.0F);
        Trail trail = trail(boss.getId(), progress);

        // name above the bar, in the bar's colour
        Component name = boss.getName();
        graphics.text(font, name, graphics.guiWidth() / 2 - font.width(name) / 2, y - 9, 0xFF000000 | palette.light, true);
        drawBar(graphics, x, y, width, 12, progress, trail, palette, true);
        String label = lifeLabel(boss, progress);
        graphics.text(font, label, graphics.guiWidth() / 2 - font.width(label) / 2, y + 2, 0xFFFFFFFF, true);
        bossBarsBottom = Math.max(bossBarsBottom, y + 22);
    }

    /** "12,340 / 18,000" from the boss entity whose life matches the bar, else the percentage. */
    private static String lifeLabel(LerpingBossEvent boss, float progress) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            String name = boss.getName().getString();
            float sum = 0;
            float sumMax = 0;
            LivingEntity best = null;
            for (LivingEntity living : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(256),
                    e -> e.isAlive() && e.getDisplayName().getString().equals(name))) {
                sum += living.getHealth();
                sumMax += living.getMaxHealth();
                if (Math.abs(living.getHealth() / living.getMaxHealth() - progress) < 0.02F
                    && (best == null || living.getMaxHealth() > best.getMaxHealth() || living instanceof TerrariaBoss)) {
                    best = living;
                }
            }
            if (best != null) {
                return NUMBERS.format(Mth.ceil(best.getHealth())) + " / " + NUMBERS.format(Math.round(best.getMaxHealth()));
            }
            if (sumMax > 0 && Math.abs(sum / sumMax - progress) < 0.03F) {
                return NUMBERS.format(Mth.ceil(sum)) + " / " + NUMBERS.format(Math.round(sumMax));
            }
        }
        return Math.round(progress * 100) + "%";
    }

    // ---------------------------------------------------------------- focus bar

    private static void renderFocus(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        int top = Math.max(6, bossBarsBottom + 4);
        bossBarsBottom = 0;
        Minecraft mc = Minecraft.getInstance();
        if (focusTicks <= 0 || mc.level == null || !enabled(TerraConfig.CLIENT.enemyHealthBar)
            || !(mc.level.getEntity(focusId) instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        if (target instanceof TerrariaBoss) {
            return;    // bosses already have their big bar
        }
        float alpha = Math.min(1.0F, focusTicks / 10.0F);
        float max = Math.max(1.0F, target.getMaxHealth());
        float life = Mth.clamp(target.getHealth(), 0.0F, max);
        float fraction = life / max;
        Trail trail = trail(focusId, fraction);
        Font font = mc.font;
        int width = 150;
        int x = graphics.guiWidth() / 2 - width / 2;

        boolean hostile = target instanceof Enemy;
        int nameColor = hostile ? 0xFFFF7A6A : 0xFF8AF08A;
        Component name = target.getDisplayName();
        String numbers = NUMBERS.format(Mth.ceil(life)) + " / " + NUMBERS.format(Math.round(max));
        // panel
        int panelTop = top;
        graphics.fill(x - 4, panelTop, x + width + 4, panelTop + 22, fade(0xB0101018, alpha));
        graphics.outline(x - 4, panelTop, width + 8, 22, fade(0xFF3A3448, alpha));
        graphics.text(font, name, x, panelTop + 3, fade(nameColor, alpha), true);
        graphics.text(font, numbers, x + width - font.width(numbers), panelTop + 3, fade(0xFFE8E8F0, alpha), true);
        Palette palette = lifePalette(fraction);
        drawBarFaded(graphics, x, panelTop + 13, width, 6, fraction, trail, palette, alpha);
    }

    // ---------------------------------------------------------------- drawing

    private static void drawBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float progress, Trail trail, Palette palette,
                                boolean ornate) {
        drawBarFaded(graphics, x, y, width, height, progress, trail, palette, 1.0F);
        if (ornate) {
            int gold = 0xFFD8B048;
            int goldDark = 0xFF7A5A1A;
            for (int side = 0; side < 2; side++) {
                int cx = side == 0 ? x - 4 : x + width + 3;
                // little diamond caps at both ends
                graphics.fill(cx - 1, y + height / 2 - 3, cx + 2, y + height / 2 + 4, goldDark);
                graphics.fill(cx - 2, y + height / 2 - 2, cx + 3, y + height / 2 + 3, gold);
                graphics.fill(cx, y + height / 2 - 1, cx + 1, y + height / 2 + 2, 0xFFFFF0B0);
            }
            graphics.horizontalLine(x - 1, x + width, y - 2, gold);
            graphics.horizontalLine(x - 1, x + width, y + height + 1, goldDark);
        }
    }

    private static void drawBarFaded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float progress, Trail trail,
                                     Palette palette, float alpha) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, fade(0xFF000000, alpha));
        graphics.fillGradient(x, y, x + width, y + height, fade(0xFF2A1016, alpha), fade(0xFF12060A, alpha));
        int filled = Math.round(width * progress);
        int lagged = Math.round(width * Mth.clamp(trail.lag, progress, 1.0F));
        if (lagged > filled) {
            graphics.fill(x + filled, y, x + lagged, y + height, fade(0xD8F0E2C8, alpha));
        }
        if (filled > 0) {
            graphics.fillGradient(x, y, x + filled, y + height, fade(0xFF000000 | palette.light, alpha), fade(0xFF000000 | palette.dark, alpha));
            graphics.fill(x, y, x + filled, y + 1, fade(0x60FFFFFF, alpha));                        // shine
            if (trail.flash > 0) {
                graphics.fill(x, y, x + filled, y + height, fade(0x70FFFFFF, alpha));               // hit flash
            }
        }
        for (int i = 1; i < 10; i++) {
            int nx = x + width * i / 10;
            graphics.fill(nx, y + 1, nx + 1, y + height - 1, fade(0x50000000, alpha));
        }
    }

    private static Palette palette(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> new Palette(0xF070D0, 0x8A2A7A);
            case BLUE -> new Palette(0x5AA8F8, 0x1E4A9A);
            case RED -> new Palette(0xF05048, 0x7A1414);
            case GREEN -> new Palette(0x6AE060, 0x1E6A1A);
            case YELLOW -> new Palette(0xF8D850, 0x9A7410);
            case PURPLE -> new Palette(0xB070F8, 0x4A1E90);
            case WHITE -> new Palette(0xF0F0F8, 0x8A8A9A);
        };
    }

    /** Green when healthy, yellow at half, red when nearly dead. */
    private static Palette lifePalette(float fraction) {
        if (fraction > 0.6F) {
            return new Palette(0x70E860, 0x1E7A1E);
        }
        if (fraction > 0.3F) {
            return new Palette(0xF8D850, 0x9A6A10);
        }
        return new Palette(0xF05048, 0x7A1414);
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static boolean enabled(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue option) {
        try {
            return option.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
