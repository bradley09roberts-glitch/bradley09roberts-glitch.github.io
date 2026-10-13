package com.grandline.client.hud;

import com.grandline.GrandLine;
import com.grandline.client.ClientState;
import com.grandline.client.Guidance;
import com.grandline.net.GLNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The focused target (top left): the locked-on foe, else whoever is under the crosshair, kept for a few seconds after
 * looking away while still in sight. Shows four separate things, each in words as well as colour:
 * <ul>
 * <li>who: rank mark and name, then role and faction;</li>
 * <li>health: a bar with numbers;</li>
 * <li>relationship to you: crew, friendly, neutral, suspicious, hostile (or sparring);</li>
 * <li>threat for you: weaker, matched, dangerous, overwhelming (minion/standard/elite/boss rank from the mark).</li>
 * </ul>
 * The server answers for targets the player can actually see only, so the frame never reveals anyone behind a wall.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class TargetFrame {
    private static int focused = -1;
    private static CompoundTag info;
    private static long lastRequest, lastSeen;

    private TargetFrame() {}

    public static void accept(CompoundTag t) {
        if (t.getInt("id") == focused) info = t;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            focused = -1;
            info = null;
            return;
        }
        long now = ClientState.clientTicks;
        LivingEntity pick = null;
        if (ClientState.lockTarget >= 0 && mc.level.getEntity(ClientState.lockTarget) instanceof LivingEntity le && le.isAlive()) pick = le;
        else if (mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive() && !(le instanceof ArmorStand) && !le.isInvisible()) pick = le;
        if (pick != null && pick.distanceToSqr(mc.player) < 32 * 32) {
            lastSeen = now;
            if (pick.getId() != focused) {
                focused = pick.getId();
                info = null;
                lastRequest = 0;
            }
        }
        // kept for three seconds after it was last under the crosshair or locked on (a lock drops when it goes out of sight)
        Entity cur = focused >= 0 ? mc.level.getEntity(focused) : null;
        if (!(cur instanceof LivingEntity le) || !le.isAlive() || now - lastSeen > 60) {
            focused = -1;
            info = null;
            return;
        }
        if (now - lastRequest >= 30) {
            lastRequest = now;
            CompoundTag q = new CompoundTag();
            q.putInt("id", focused);
            PacketDistributor.sendToServer(new GLNetwork.UiAction("target_info", q));
        }
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || focused < 0 || info == null || mc.getDebugOverlay().showDebugScreen()) return;
        if (!(mc.level.getEntity(focused) instanceof LivingEntity e) || !e.isAlive()) return;
        Font font = mc.font;
        int x = 6, y = 6, w = 168;
        String rel = info.getString("rel"), rank = info.getString("rank"), threat = info.getString("threat");
        int relColor = switch (rel) {
            case "hostile" -> 0xFFFF5A4A;
            case "suspicious" -> 0xFFFFB040;
            case "friendly" -> 0xFF7CDC6A;
            case "crew" -> 0xFF6AB8FF;
            case "sparring" -> 0xFF6ADCC8;
            default -> 0xFFC8C8C8;
        };
        int h = 50;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xC0101418);
        g.fill(x - 2, y - 2, x + w + 2, y - 1, relColor);
        // rank mark + name
        String mark = switch (rank) {
            case "boss" -> "♛ ";   // ♛
            case "elite" -> "◆ ";  // ◆
            case "minion" -> "· "; // ·
            default -> "";
        };
        int markColor = rank.equals("boss") ? 0xFFFFC83C : rank.equals("elite") ? 0xFFD8D8E8 : 0xFF909090;
        Component name = Guidance.parse(info, "name");
        g.drawString(font, mark, x + 2, y + 2, markColor, true);
        g.drawString(font, name == null ? e.getDisplayName() : name, x + 2 + font.width(mark), y + 2, 0xFFFFFFFF, true);
        // role · faction
        Component role = Guidance.parse(info, "role"), faction = Guidance.parse(info, "faction");
        Component sub = role != null && faction != null ? Component.translatable("hud.grandline.target.role_faction", role, faction)
                : role != null ? role : faction;
        if (sub != null) g.drawString(font, sub, x + 2, y + 12, 0xFFA8A8A8, false);
        // health
        float hp = e.getHealth(), max = Math.max(1, e.getMaxHealth());
        int by = y + 23;
        g.fill(x + 2, by, x + w - 2, by + 6, 0xFF000000);
        g.fill(x + 3, by + 1, x + 3 + (int) ((w - 6) * Math.min(1f, hp / max)), by + 5, rel.equals("hostile") ? 0xFFD03A2A : 0xFF4CB04C);
        String hpText = (int) Math.ceil(hp) + " / " + (int) Math.ceil(max);
        g.drawString(font, hpText, x + w - 2 - font.width(hpText), by - 9 + 0, 0xFFE0E0E0, false);
        // relationship + threat, in words
        Component relText = Component.translatable("hud.grandline.target.rel." + rel);
        g.drawString(font, relText, x + 2, y + 34, relColor, true);
        if (!threat.isEmpty() && !rel.equals("crew") && !rel.equals("sparring")) {
            int tc = switch (threat) {
                case "weaker" -> 0xFF8CC8FF;
                case "matched" -> 0xFFE8E8E8;
                case "dangerous" -> 0xFFFFB040;
                default -> 0xFFFF4040;
            };
            Component th = Component.translatable("hud.grandline.target.threat." + threat);
            g.drawString(font, th, x + w - 2 - font.width(th), y + 34, tc, true);
        }
    }
}
