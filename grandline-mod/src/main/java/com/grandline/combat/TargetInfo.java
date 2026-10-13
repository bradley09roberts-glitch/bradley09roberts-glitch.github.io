package com.grandline.combat;

import com.grandline.balance.Tuning;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.Characters;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who someone is, for the focused-target frame: name, role, faction, how they stand towards this player
 * (crew / friendly / neutral / suspicious / hostile), their rank (standard / elite / boss) and how dangerous they are
 * for this player (weaker / matched / dangerous / overwhelming, see {@link Tuning#threat}). Sent only for someone the
 * player can actually see. Also the warning when a dangerous foe turns on a player.
 */
public final class TargetInfo {
    private static final Map<UUID, Map<Integer, Long>> WARNED = new HashMap<>();

    private TargetInfo() {}

    /** The client focused an entity (lock-on or crosshair): answer with what the frame shows. */
    public static void request(ServerPlayer sp, int entityId) {
        Entity e = sp.level().getEntity(entityId);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.distanceToSqr(sp) > 48 * 48 || !Cover.canSee(sp, le)) return;
        GLNetwork.toPlayer(sp, new GLNetwork.TargetInfo(describe(sp, le)));
    }

    public static CompoundTag describe(ServerPlayer sp, LivingEntity e) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", e.getId());
        t.putString("name", Component.Serializer.toJson(e.getDisplayName(), sp.registryAccess()));
        String rel;
        Component role = null, faction = null;
        if (e instanceof GLNpc n) {
            role = Component.translatable("role.grandline." + n.role());
            faction = Component.translatable(n.faction().translationKey());
            if (com.grandline.npc.crew.CrewManager.isCrewOf(n, sp)) rel = "crew";
            else if (Sparring.isPartner(n)) rel = "sparring";
            else if (n.getTarget() == sp || n.isHostileTo(sp)) rel = "hostile";
            else {
                int att = n.attitudeTo(sp);
                rel = att >= 40 ? "friendly" : att >= -10 ? "neutral" : "suspicious";
            }
        } else if (e instanceof Player p) {
            var a = Characters.get(sp);
            var b = Characters.get(p);
            rel = a.crewId != null && a.crewId.equals(b.crewId) ? "crew" : "neutral";
            faction = Component.translatable(b.faction.translationKey());
        } else if (e instanceof Enemy || e instanceof com.grandline.creature.SeaKing) {
            rel = "hostile";
        } else if (e instanceof com.grandline.creature.WildBeast wb) {
            rel = wb.getTarget() == sp || wb.kind.hostile ? "hostile" : "neutral";
        } else {
            rel = e instanceof Animal ? "neutral" : "neutral";
        }
        if (role != null) t.putString("role", Component.Serializer.toJson(role, sp.registryAccess()));
        if (faction != null) t.putString("faction", Component.Serializer.toJson(faction, sp.registryAccess()));
        t.putString("rel", rel);
        if (!(e instanceof Player)) {
            t.putString("rank", Tuning.rank(e).id());
            t.putString("threat", Tuning.threat(sp, e).name().toLowerCase(java.util.Locale.ROOT));
        }
        return t;
    }

    /**
     * A foe has just turned on a player: if it is dangerous or overwhelming for them, say so once (per foe, per minute)
     * before the first blow, with a warning chime.
     */
    public static void warnIfDangerous(ServerPlayer sp, LivingEntity foe) {
        Tuning.Threat th = Tuning.threat(sp, foe);
        if (th.ordinal() < Tuning.Threat.DANGEROUS.ordinal()) return;
        long now = sp.level().getGameTime();
        Map<Integer, Long> m = WARNED.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
        Long last = m.get(foe.getId());
        if (last != null && now - last < 1200) return;
        m.put(foe.getId(), now);
        if (m.size() > 64) m.values().removeIf(t -> now - t > 1200);
        boolean over = th == Tuning.Threat.OVERWHELMING;
        sp.displayClientMessage(Component.translatable(over ? "grandline.threat.warn.overwhelming" : "grandline.threat.warn.dangerous", foe.getDisplayName())
                .withStyle(over ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 0.8f, over ? 0.5f : 0.7f);
    }
}
