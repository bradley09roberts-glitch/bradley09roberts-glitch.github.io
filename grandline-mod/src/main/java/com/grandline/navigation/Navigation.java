package com.grandline.navigation;

import com.grandline.net.GLNetwork;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.QuestEvents;
import com.grandline.registry.ModDataComponents;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Discovery, Log Pose and Eternal Pose. In the Grand Line, compasses are useless; a Log Pose records an island's
 * magnetism after you stay there (3 in-game minutes) and then points to the next island along the route.
 */
public final class Navigation {
    public static final int LOG_TICKS = 20 * 60 * 3;

    public static void tick(ServerPlayer sp, CharacterData d) {
        if (sp.tickCount % 40 != 0) return;
        WorldPlan plan = WorldPlan.forLevel(sp.level()); // ocean world, White Sea or deep sea
        if (plan == null) return;
        // just arrived where the ground is still generating (join, teleport, respawn): discover once it has loaded,
        // so the level-up below does not make the server thread wait for the player's own chunk
        if (!com.grandline.core.Chunks.ready(sp.level(), sp.getBlockX(), sp.getBlockZ())) return;
        Island isl = plan.islandAt(sp.getBlockX(), sp.getBlockZ());
        if (isl != null && isl.t(sp.getBlockX(), sp.getBlockZ()) < 1.0) {
            if (d.discoveredIslands.add(isl.id)) {
                Component name = isl.canon && isl.name == null ? Component.translatable(isl.translationKey()) : Component.literal(isl.name);
                sp.sendSystemMessage(Component.translatable("grandline.discover", name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(name.copy().withStyle(ChatFormatting.GOLD)));
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("zone.grandline." + isl.zone.id())));
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.2f);
                int xp = 40 + isl.tier * 30 + (isl.canon ? 60 : 0);
                if (d.addXp(xp) > 0) CharacterManager.applyAttributes(sp);
                QuestEvents.onDiscover(sp, isl.id);
                if (isl.canon) QuestEvents.onDiscover(sp, "any_canon");
                QuestEvents.onDiscover(sp, "any");
                CharacterManager.markDirty(sp);
            }
            // Log Pose recording
            String key = "log:" + isl.id;
            int stayed = d.masteryXp.getOrDefault(key, 0) + 40;
            d.masteryXp.put(key, Math.min(stayed, LOG_TICKS));
        }
    }

    public static String compass(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        if (ang < 0) ang += 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(ang / 45) % 8];
    }

    /** Next island along the Grand Line route from {@code from} (Paradise flows east, New World flows east). */
    @Nullable
    public static Island nextOnRoute(WorldPlan plan, Island from) {
        Island best = null;
        double bd = Double.MAX_VALUE;
        for (Island i : plan.islands) {
            if (i == from || i.radius < 10) continue;
            if (!(i.zone.isGrandLine() || i.zone == SeaZone.CALM_BELT)) continue;
            if (i.zone != from.zone && from.zone.isGrandLine()) continue;
            if (i.x <= from.x + 300) continue;
            double d = i.distance(from.x, from.z) + Math.abs(i.z - from.z) * 0.4;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    public static void readLogPose(ServerPlayer sp, ItemStack stack) {
        CharacterData d = Characters.get(sp);
        WorldPlan plan = WorldPlan.of(sp.server);
        if (plan == null) return;
        SeaZone here = WorldLayout.zoneAt(sp.getBlockX(), sp.getBlockZ());
        // find the most recently recorded island (fully logged)
        Island current = plan.islandAt(sp.getBlockX(), sp.getBlockZ());
        if (current == null) {
            String t = stack.get(ModDataComponents.POSE_TARGET.get());
            if (t == null) {
                sp.sendSystemMessage(Component.translatable("grandline.logpose.unset").withStyle(ChatFormatting.GRAY));
                return;
            }
            pointTo(sp, plan, t);
            return;
        }
        int stayed = d.masteryXp.getOrDefault("log:" + current.id, 0);
        if (stayed < LOG_TICKS) {
            sp.sendSystemMessage(Component.translatable("grandline.logpose.recording", (LOG_TICKS - stayed) / 20).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!(here.isGrandLine() || here == SeaZone.CALM_BELT)) {
            sp.sendSystemMessage(Component.translatable("grandline.logpose.blues").withStyle(ChatFormatting.GRAY));
            return;
        }
        Island next = nextOnRoute(plan, current);
        if (next == null) {
            sp.sendSystemMessage(Component.translatable("grandline.logpose.end").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        stack.set(ModDataComponents.POSE_TARGET.get(), next.id + "|" + next.x + "|" + next.z);
        d.logPoseTarget = next.id;
        pointTo(sp, plan, stack.get(ModDataComponents.POSE_TARGET.get()));
        CharacterManager.markDirty(sp);
    }

    public static void readEternalPose(ServerPlayer sp, ItemStack stack) {
        WorldPlan plan = WorldPlan.of(sp.server);
        String t = stack.get(ModDataComponents.POSE_TARGET.get());
        if (plan == null || t == null) return;
        pointTo(sp, plan, t);
    }

    private static void pointTo(ServerPlayer sp, WorldPlan plan, String target) {
        String id = target.split("\\|")[0];
        Island i = plan.byId(id);
        if (i == null) return;
        double dx = i.x - sp.getX(), dz = i.z - sp.getZ();
        Component name = i.canon && i.name == null ? Component.translatable(i.translationKey()) : Component.literal(i.name);
        sp.sendSystemMessage(Component.translatable("grandline.pose.points", name, compass(dx, dz), (int) Math.sqrt(dx * dx + dz * dz)).withStyle(ChatFormatting.AQUA));
    }

    public static ItemStack eternalPose(Island i) {
        ItemStack s = new ItemStack(com.grandline.registry.ModItems.ETERNAL_POSE.get());
        s.set(ModDataComponents.POSE_TARGET.get(), i.id + "|" + i.x + "|" + i.z);
        return s;
    }

    public static void openChart(ServerPlayer sp) {
        WorldPlan plan = WorldPlan.of(sp.server);
        if (plan == null) return;
        CharacterData d = Characters.get(sp);
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Island i : plan.islands) {
            boolean known = d.discoveredIslands.contains(i.id);
            if (!known && !(i.canon && i.zone.isBlue())) continue; // Blue charts are common knowledge; the Grand Line must be discovered
            CompoundTag e = new CompoundTag();
            e.putString("id", i.id);
            e.putString("name", i.canon && i.name == null ? "" : i.name);
            e.putInt("x", i.x);
            e.putInt("z", i.z);
            e.putInt("r", i.radius);
            e.putBoolean("known", known);
            e.putString("zone", i.zone.id());
            e.putInt("tier", i.tier);
            l.add(e);
        }
        t.put("islands", l);
        t.putInt("px", sp.getBlockX());
        t.putInt("pz", sp.getBlockZ());
        t.putInt("total", plan.islands.size());
        t.putInt("discovered", d.discoveredIslands.size());
        GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("chart", t));
    }

    private Navigation() {}
}
