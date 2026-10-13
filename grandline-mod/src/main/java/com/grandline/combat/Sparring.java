package com.grandline.combat;

import com.grandline.GrandLine;
import com.grandline.net.GLNetwork;
import com.grandline.npc.HumanoidNpc;
import com.grandline.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Training bouts: a sparring partner who fights only the player who asked, telegraphs plainly, and never hurts them
 * (its blows land for practice but deal no damage). Good for learning the loop — strike, read the wind-up, guard or
 * dodge, punish — and it completes the elder's "lesson" objectives like a real fight. The bout ends when the partner
 * is worn down to a third of its health, after two minutes, or when the player walks away; the partner then bows out.
 * Started from an elder's or a master's dialogue, or with /spar.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class Sparring {
    public static final String TAG = "grandline_sparring";
    private static final int MAX_TICKS = 20 * 120;
    /** partner entity id -> bout */
    private static final Map<Integer, Bout> BOUTS = new HashMap<>();

    private record Bout(UUID player, HumanoidNpc partner, long started) {}

    private Sparring() {}

    /** Start a bout for the player (one at a time); {@code by} is who offered it (an elder or master), or null. */
    public static boolean start(ServerPlayer sp, @Nullable LivingEntity by) {
        for (Bout b : BOUTS.values()) {
            if (b.player.equals(sp.getUUID())) {
                sp.displayClientMessage(Component.translatable("grandline.spar.already").withStyle(ChatFormatting.GRAY), true);
                return false;
            }
        }
        ServerLevel l = sp.serverLevel();
        Vec3 look = sp.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        Vec3 at = sp.position().add(look.normalize().scale(4));
        BlockPos ground = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        if (Math.abs(ground.getY() - sp.getY()) > 3) ground = sp.blockPosition();
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        if (n == null) return false;
        n.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, sp.getYRot() + 180, 0);
        n.configure("sparring_partner", 1, "", BlockPos.ZERO, "", l.random);
        n.clearHakiForTest();
        n.addTag(TAG);
        n.setCustomName(by != null ? Component.translatable("grandline.spar.partner_of", by.getDisplayName()) : Component.translatable("grandline.spar.partner"));
        n.setCustomNameVisible(true);
        l.addFreshEntity(n);
        n.setTarget(sp);
        BOUTS.put(n.getId(), new Bout(sp.getUUID(), n, l.getGameTime()));
        sp.sendSystemMessage(Component.translatable("grandline.spar.start").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    public static boolean isPartner(Entity e) {
        return e.getTags().contains(TAG);
    }

    /** A partner is hostile only to the player it spars with. */
    public static boolean spars(Entity partner, Entity other) {
        Bout b = BOUTS.get(partner.getId());
        return b != null && other.getUUID().equals(b.player);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (BOUTS.isEmpty()) return;
        for (Iterator<Map.Entry<Integer, Bout>> it = BOUTS.entrySet().iterator(); it.hasNext(); ) {
            Bout b = it.next().getValue();
            HumanoidNpc n = b.partner;
            ServerPlayer sp = e.getServer().getPlayerList().getPlayer(b.player);
            boolean beaten = n.isAlive() && n.getHealth() <= n.getMaxHealth() / 3;
            boolean over = !n.isAlive() || n.isRemoved() || sp == null || sp.level() != n.level() || sp.distanceToSqr(n) > 24 * 24
                    || n.level().getGameTime() - b.started > MAX_TICKS;
            if (!beaten && !over) {
                if (n.getTarget() != sp) n.setTarget(sp);
                continue;
            }
            if (sp != null) sp.sendSystemMessage(Component.translatable(beaten ? "grandline.spar.won" : "grandline.spar.ended").withStyle(ChatFormatting.YELLOW));
            Vec3 p = n.position();
            GLNetwork.fx(n, "dodge", p.x, p.y + 1, p.z, 0, 0, 0);
            n.discard();
            it.remove();
        }
    }

    /** /spar: a training bout anywhere it is calm (no foe is after you). */
    @SubscribeEvent
    public static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("spar").executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            if (!com.grandline.player.Characters.get(sp).created) return 0;
            boolean threatened = !sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(24),
                    m -> m.getTarget() == sp && !isPartner(m)).isEmpty();
            if (threatened) {
                sp.displayClientMessage(Component.translatable("grandline.spar.not_now").withStyle(ChatFormatting.GRAY), true);
                return 0;
            }
            return start(sp, null) ? 1 : 0;
        }));
    }

    /** Partners are never saved into the world: one that loads from disk (a bout cut short by a restart) leaves. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide && e.loadedFromDisk() && isPartner(e.getEntity())) e.setCanceled(true);
    }
}
