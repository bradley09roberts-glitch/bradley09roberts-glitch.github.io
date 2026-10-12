package com.grandline.career;

import com.grandline.GrandLine;
import com.grandline.journey.Journey;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.QuestRegistry;
import com.grandline.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The Warlord charter for players (docs/STORY.md §4). A Supernova who has not finished the war may be visited by a
 * World Government envoy (once a day while on land; declining keeps them away for three days) offering a letter of
 * marque: one of seven seats. A Warlord's bounty is frozen and the Marines (patrols, bases and warships) stand down;
 * in exchange the Warlord hunts pirates: three pirates (a pirate ship counts three) every three days, paid with a
 * share of the plunder. The letter is revoked for neglecting that duty, for any crime that would raise the bounty and
 * for striking the Marines or the Government: the bounty comes back (at least 50M), the seat is lost and no second
 * letter is ever offered. The end of the war abolishes the charter; an Emperor renounces it.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class Warlords {
    public static final String REVOKED = "warlord_revoked", ENVOY_ROLE = "wg_envoy", ENVOY_TAG = "gl_wg_envoy", ENVOY_FOR = "gl_envoy_for:";
    public static final long DUTY_TICKS = 24000L * 3, ENVOY_TICKS = 20 * 60 * 3;
    public static final int QUOTA = 3;
    public static final long SHARE = 10_000;

    private record Envoy(ServerLevel level, UUID target, long until) {}

    private static final Map<UUID, Envoy> ENVOYS = new HashMap<>();

    /** Whether the Government would offer this pirate a letter now (all but the envoy's visit). */
    public static boolean eligible(ServerPlayer sp, CharacterData d) {
        return Careers.ensure(sp, d) == CareerTrack.PIRATE && Careers.rank(d) == 1 && !d.career.warlord && !d.flags.contains(REVOKED)
                && !QuestRegistry.isDone(d, "war_3") && CareerWorldData.get(sp.server).open(CareerWorldData.WARLORD, d.characterId);
    }

    /** Every ten seconds: envoys for eligible pirates on land; envoys whose time is up leave. */
    static void envoys(MinecraftServer s) {
        long now = Careers.now(s);
        ENVOYS.entrySet().removeIf(en -> {
            Entity e = en.getValue().level.getEntity(en.getKey());
            if (e == null) return true;
            if (now < en.getValue().until) return false;
            e.discard();
            return true;
        });
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) {
            CharacterData d = Characters.get(sp);
            if (d.career.envoyReady > now || !sp.onGround() || sp.isPassenger() || sp.getLastHurtByMob() != null || !eligible(sp, d)) continue;
            if (envoyFor(sp) == null) spawnEnvoy(sp);
        }
    }

    @Nullable
    static GLNpc envoyFor(ServerPlayer sp) {
        for (var en : ENVOYS.entrySet())
            if (en.getValue().target.equals(sp.getUUID()) && en.getValue().level.getEntity(en.getKey()) instanceof GLNpc n && n.isAlive()) return n;
        return null;
    }

    /** A World Government envoy steps up to the pirate with a sealed letter. */
    @Nullable
    public static HumanoidNpc spawnEnvoy(ServerPlayer sp) {
        ServerLevel l = sp.serverLevel();
        BlockPos at = NotableSpawner.safeSpotNear(l, sp.blockPosition());
        if (at == null) at = sp.blockPosition();
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        if (n == null) return null;
        n.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, sp.getYRot() + 180, 0);
        n.configure(ENVOY_ROLE, 6, "", BlockPos.ZERO, "", l.random);
        n.addTag(ENVOY_TAG);
        n.addTag(ENVOY_FOR + sp.getUUID());
        if (!l.addFreshEntity(n)) return null;
        long now = Careers.now(sp.server);
        ENVOYS.put(n.getUUID(), new Envoy(l, sp.getUUID(), now + ENVOY_TICKS));
        Characters.get(sp).career.envoyReady = now + 24000;
        l.sendParticles(ParticleTypes.CLOUD, n.getX(), n.getY() + 1, n.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        Careers.tell(sp, Component.translatable("grandline.career.warlord.envoy").withStyle(ChatFormatting.LIGHT_PURPLE));
        CharacterManager.markDirty(sp);
        return n;
    }

    public static boolean isEnvoyFor(GLNpc npc, Player p) {
        return npc.getTags().contains(ENVOY_FOR + p.getUUID());
    }

    static void leave(GLNpc envoy) {
        ENVOYS.remove(envoy.getUUID());
        if (envoy.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.CLOUD, envoy.getX(), envoy.getY() + 1, envoy.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        envoy.discard();
    }

    /** The pirate accepts the letter of marque. */
    public static boolean grant(ServerPlayer sp, @Nullable GLNpc envoy) {
        CharacterData d = Characters.get(sp);
        if (!eligible(sp, d)) return false;
        long now = Careers.now(sp.server);
        if (!CareerWorldData.get(sp.server).take(CareerWorldData.WARLORD, d.characterId, sp.getUUID(), d.name, now)) return false;
        CareerState c = d.career;
        c.warlord = true;
        c.warlordSince = now;
        c.dutyDue = now + DUTY_TICKS;
        c.dutyDone = 0;
        ItemStack letter = new ItemStack(CareerRegistry.LETTER_OF_MARQUE.get());
        CompoundTag t = new CompoundTag();
        t.putString("holder", d.name);
        t.putUUID("character", d.characterId);
        letter.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        if (!sp.getInventory().add(letter)) sp.drop(letter, false);
        Careers.tell(sp, Component.translatable("grandline.career.warlord.granted", QUOTA, DUTY_TICKS / 24000).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        Careers.announce(sp.server, Component.translatable("grandline.career.announce.warlord", d.name).withStyle(ChatFormatting.LIGHT_PURPLE));
        Journey.milestone(sp, Careers.MILESTONES.get("pirate:warlord"));
        if (envoy != null) leave(envoy);
        GrandLine.LOGGER.info("Career: {} accepted a letter of marque", d.name);
        Careers.changed(sp);
        CharacterManager.markDirty(sp);
        return true;
    }

    public static void decline(ServerPlayer sp, @Nullable GLNpc envoy) {
        CharacterData d = Characters.get(sp);
        d.career.envoyReady = Careers.now(sp.server) + 24000L * 3;
        Careers.tell(sp, Component.translatable("grandline.career.warlord.declined").withStyle(ChatFormatting.GRAY));
        if (envoy != null) leave(envoy);
        CharacterManager.markDirty(sp);
    }

    /** Pirates hunted for the Government. */
    static void duty(ServerPlayer sp, CharacterData d, int n) {
        d.career.dutyDone += n;
        sp.displayClientMessage(Component.translatable("grandline.career.warlord.duty", Math.min(d.career.dutyDone, QUOTA), QUOTA).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** The end of a duty period: the quota met earns the Government's share; missed, the letter is revoked. */
    static void tick(ServerPlayer sp, CharacterData d) {
        CareerState c = d.career;
        if (!c.warlord) return;
        long now = Careers.now(sp.server);
        if (QuestRegistry.isDone(d, "war_3")) {
            abolish(sp, d);
            return;
        }
        if (now < c.dutyDue) return;
        if (c.dutyDone >= QUOTA) {
            c.dutyDue = now + DUTY_TICKS;
            c.dutyDone = 0;
            d.beli += SHARE;
            Careers.tell(sp, Component.translatable("grandline.career.warlord.duty_met", String.format("%,d", SHARE)).withStyle(ChatFormatting.LIGHT_PURPLE));
            CharacterManager.markDirty(sp);
            return;
        }
        long restored = revoke(sp, d, Component.translatable("grandline.career.warlord.breach_duty"));
        if (restored > 0) com.grandline.npc.Bounties.raise(sp, d, restored, Component.translatable("grandline.career.warlord.revoked_reason"));
    }

    /**
     * The Government revokes the letter: the seat is lost and no letter is offered again. Returns the bounty to add
     * back (half the frozen bounty, at least 50M); the caller raises it.
     */
    public static long revoke(ServerPlayer sp, CharacterData d, Component why) {
        if (!d.career.warlord) return 0;
        d.career.warlord = false;
        d.flags.add(REVOKED);
        CareerWorldData.get(sp.server).leave(CareerWorldData.WARLORD, d.characterId);
        Squads.dismiss(sp);
        long restored = Math.max(50_000_000L, d.bounty / 2);
        Careers.tell(sp, Component.translatable("grandline.career.warlord.revoked", why).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        Careers.announce(sp.server, Component.translatable("grandline.career.announce.warlord_revoked", d.name, why).withStyle(ChatFormatting.DARK_RED));
        GrandLine.LOGGER.info("Career: {}'s letter of marque revoked", d.name);
        Careers.changed(sp);
        CharacterManager.markDirty(sp);
        return restored;
    }

    /** The war is over: the charter burns and every letter is void (no penalty, no protection). */
    static void abolish(ServerPlayer sp, CharacterData d) {
        if (!d.career.warlord) return;
        d.career.warlord = false;
        CareerWorldData.get(sp.server).leave(CareerWorldData.WARLORD, d.characterId);
        Careers.tell(sp, Component.translatable("grandline.career.warlord.abolished").withStyle(ChatFormatting.GOLD));
        Careers.changed(sp);
        CharacterManager.markDirty(sp);
    }

    /** Promoted to Emperor: no Emperor serves the Government. */
    static void renounce(ServerPlayer sp, CharacterData d) {
        d.career.warlord = false;
        CareerWorldData.get(sp.server).leave(CareerWorldData.WARLORD, d.characterId);
        Careers.tell(sp, Component.translatable("grandline.career.warlord.renounced").withStyle(ChatFormatting.GOLD));
    }

    /** Envoys are transient. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.loadedFromDisk() && e.getEntity().getTags().contains(ENVOY_TAG)) e.setCanceled(true);
    }

    static void clear() {
        ENVOYS.clear();
    }

    private Warlords() {}
}
