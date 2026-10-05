package com.squidgame.tournament;

import com.squidgame.SquidConfig;
import com.squidgame.SquidGameMod;
import com.squidgame.registry.ModItems;
import com.squidgame.world.ArenaWorld;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Arena rules for everyone inside the tournament dimension (or otherwise taking part): no building or breaking,
 * no flying, no teleporting, no outside equipment, no vanilla damage. Operators in creative may bypass when the
 * config allows. Settings are restored from the player's snapshot when they leave.
 */
public final class Restrictions {
    private static final Set<Item> ALLOWED_ITEMS = Set.of();
    private static final double TELEPORT_SNAP_DISTANCE = 12.0;

    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final Map<UUID, Integer> grace = new HashMap<>();
    private final Map<UUID, GameType> desired = new HashMap<>();

    // ------------------------------------------------------------------ static predicates (used by event handlers)

    public static boolean isParticipant(ServerPlayer p) {
        if (ArenaWorld.isArena(p.level())) {
            return true;
        }
        TournamentManager m = TournamentManager.get();
        return m != null && m.isInvolved(p);
    }

    public static boolean bypasses(Player p) {
        return SquidConfig.get().allowOpsToBypassRules && p.hasPermissions(2) && p.isCreative();
    }

    public static boolean isRestricted(Player p) {
        if (!SquidConfig.get().enforceRules || !(p instanceof ServerPlayer sp)) {
            return false;
        }
        return isParticipant(sp) && !bypasses(p);
    }

    public static boolean isItemAllowed(ItemStack s) {
        if (s.isEmpty()) {
            return true;
        }
        Item i = s.getItem();
        return i == ModItems.RECRUITER_CARD || i == ModItems.MARBLE || ALLOWED_ITEMS.contains(i);
    }

    public static void noteTeleport(ServerPlayer p) {
        TournamentManager m = TournamentManager.get();
        if (m != null) {
            m.restrictions().grace.put(p.getUUID(), 5);
            m.restrictions().lastPos.put(p.getUUID(), p.position());
        }
    }

    // ------------------------------------------------------------------ instance

    public void setDesiredMode(ServerPlayer p, GameType mode) {
        desired.put(p.getUUID(), mode);
    }

    public void forget(UUID id) {
        desired.remove(id);
        lastPos.remove(id);
        grace.remove(id);
    }

    /** Per-tick enforcement for one participant. */
    public void tickPlayer(ServerPlayer p, boolean allowFlight) {
        if (!isRestricted(p)) {
            return;
        }
        GameType want = desired.getOrDefault(p.getUUID(), GameType.ADVENTURE);
        if (p.gameMode.getGameModeForPlayer() != want) {
            p.setGameMode(want);
        }
        var ab = p.getAbilities();
        if (want != GameType.SPECTATOR && !allowFlight && (ab.mayfly || ab.flying)) {
            ab.mayfly = false;
            ab.flying = false;
            p.onUpdateAbilities();
        }
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
        if (p.tickCount % 10 == 0) {
            stripForbiddenItems(p);
        }
        // anti-teleport: any jump larger than the snap distance that we did not cause is undone
        Vec3 now = p.position();
        UUID id = p.getUUID();
        int g = grace.getOrDefault(id, 0);
        Vec3 last = lastPos.get(id);
        if (g > 0) {
            grace.put(id, g - 1);
        } else if (last != null && want != GameType.SPECTATOR && now.distanceToSqr(last) > TELEPORT_SNAP_DISTANCE * TELEPORT_SNAP_DISTANCE
                && p.level().dimension().equals(ArenaWorld.DIMENSION)) {
            // the level variant sends the position packet to the client; Entity#teleportTo alone would leave the client in place
            p.teleportTo(p.serverLevel(), last.x, last.y, last.z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            SquidGameMod.debug("Restrictions: undid an unauthorised teleport of {} ({} blocks)", p.getGameProfile().getName(),
                    (int) Math.sqrt(now.distanceToSqr(last)));
            p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("squidgame.rule.no_teleport"));
            Announcer.sound(p, com.squidgame.registry.ModSounds.UI_DENY, 1f, 1f);
            return;
        }
        lastPos.put(id, now);
    }

    private void stripForbiddenItems(ServerPlayer p) {
        var inv = p.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && !isItemAllowed(s)) {
                inv.setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            p.containerMenu.broadcastChanges();
        }
    }

    // ------------------------------------------------------------------ vanilla commands

    private static final Set<String> BLOCKED_COMMANDS = Set.of("tp", "teleport", "gamemode", "spreadplayers", "kill",
            "spawnpoint", "setworldspawn", "give", "item", "clear", "effect", "enchant", "fill", "setblock", "clone", "place",
            "summon", "attribute");

    /**
     * Wraps the requirement of risky vanilla commands so tournament participants cannot use them. Called once after
     * the command dispatcher is built.
     */
    public static void wrapVanillaCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        for (String name : BLOCKED_COMMANDS) {
            var node = dispatcher.getRoot().getChild(name);
            if (node == null) {
                continue;
            }
            Predicate<net.minecraft.commands.CommandSourceStack> original = node.getRequirement();
            Predicate<net.minecraft.commands.CommandSourceStack> wrapped = src -> original.test(src)
                    && !(src.getEntity() instanceof ServerPlayer sp && isRestricted(sp));
            try {
                var f = com.mojang.brigadier.tree.CommandNode.class.getDeclaredField("requirement");
                f.setAccessible(true);
                f.set(node, wrapped);
            } catch (ReflectiveOperationException | RuntimeException e) {
                SquidGameMod.LOGGER.warn("Could not restrict /{}: {}", name, e.toString());
            }
        }
    }

    // ------------------------------------------------------------------ events

    public static void registerEvents() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !isRestricted(player));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!isRestricted(player)) {
                return InteractionResult.PASS;
            }
            Item held = player.getItemInHand(hand).getItem();
            if (held instanceof BlockItem || held instanceof BucketItem || held instanceof FlintAndSteelItem
                    || held instanceof SpawnEggItem || held == Items.BONE_MEAL || held == Items.ARMOR_STAND) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!isRestricted(player)) {
                return InteractionResultHolder.pass(player.getItemInHand(hand));
            }
            Item held = player.getItemInHand(hand).getItem();
            if (held instanceof EnderpearlItem || held instanceof ChorusFruitItem || held instanceof FireworkRocketItem
                    || held instanceof TridentItem || held == Items.WIND_CHARGE) {
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer sp) || !isRestricted(player)) {
                return InteractionResult.PASS;
            }
            TournamentManager m = TournamentManager.get();
            boolean cancel = m == null || m.onPlayerAttack(sp, entity);
            return cancel ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        // vanilla damage never hurts participants; eliminations are decided by game rules
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer sp && isRestricted(sp)) {
                return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && source.getEntity() == null
                        && !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD) ? true : false;
            }
            return true;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer sp && isParticipant(sp) && !bypasses(sp)) {
                TournamentManager m = TournamentManager.get();
                if (m != null) {
                    m.onParticipantWouldDie(sp);
                }
                return false;
            }
            return true;
        });
    }

    /** Unused marker so the import of BlockPos stays meaningful for future region checks. */
    static BlockPos origin() {
        return BlockPos.ZERO;
    }

    static InteractionHand mainHand() {
        return InteractionHand.MAIN_HAND;
    }
}
