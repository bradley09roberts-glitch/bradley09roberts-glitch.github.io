package com.grandline.creature;

import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.fruit.Zoan;
import com.grandline.npc.HumanoidNpc;
import com.grandline.registry.ModEntities;
import com.grandline.world.WorldPlan;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Creature test helpers under /grandline test (op only), used by the scripted client test:
 * <ul>
 *   <li>{@code seaking <variant|random> <tier> [hold] [distance]} — a Sea King broadside in front of you; "hold" keeps it
 *   surfaced and passive for inspection</li>
 *   <li>{@code zoan <none|hybrid|full>} — set your Zoan form directly (skips the mastery requirement)</li>
 *   <li>{@code wildlife <kind> <offset> <distance>} — a still wild beast in front of you</li>
 *   <li>{@code beastnpc <fruit> <offset> [distance]} — a still NPC in that fruit's full beast form, offset sideways in front of you</li>
 * </ul>
 */
public final class CreatureTestCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> seaKing() {
        return Commands.literal("seaking").then(Commands.argument("variant", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Arrays.stream(SeaKing.VARIANTS), Stream.of("random")), b))
                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                        .executes(c -> seaKing(c, false, 0))
                        .then(Commands.literal("hold").executes(c -> seaKing(c, true, 0))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(8, 120)).executes(c -> seaKing(c, true, IntegerArgumentType.getInteger(c, "distance")))))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> zoan() {
        return Commands.literal("zoan").then(Commands.argument("form", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.of("none", "hybrid", "full"), b))
                .executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    String form = StringArgumentType.getString(c, "form");
                    if (form.equals("none")) form = "";
                    var d = com.grandline.player.Characters.get(sp);
                    DevilFruit f = d.fruit == null ? null : DevilFruits.get(d.fruit);
                    if (f == null || !f.type.isZoan()) {
                        c.getSource().sendFailure(Component.literal("Not a Zoan user"));
                        return 0;
                    }
                    Zoan.setForm(sp, form);
                    String model = BeastForms.modelFor(d.fruit);
                    String shown = form;
                    c.getSource().sendSuccess(() -> Component.literal("Zoan form: " + (shown.isEmpty() ? "none" : shown) + " (" + model + ") hitbox "
                            + String.format("%.2fx%.2f", sp.getBbWidth(), sp.getBbHeight())), false);
                    return 1;
                }));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> wildlife() {
        return Commands.literal("wildlife").then(Commands.argument("kind", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(WildBeast.Kind.values()).map(WildBeast.Kind::id), b))
                .then(Commands.argument("offset", IntegerArgumentType.integer(-40, 40)).then(Commands.argument("distance", IntegerArgumentType.integer(2, 60)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    WildBeast.Kind kind;
                    try {
                        kind = WildBeast.Kind.valueOf(StringArgumentType.getString(c, "kind").toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        c.getSource().sendFailure(Component.literal("Unknown wildlife"));
                        return 0;
                    }
                    double yaw = Math.toRadians(sp.getYRot());
                    Vec3 p = sp.position().add(new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).scale(IntegerArgumentType.getInteger(c, "distance")))
                            .add(new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(IntegerArgumentType.getInteger(c, "offset")));
                    ServerLevel l = sp.serverLevel();
                    int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z));
                    WildBeast b = WildlifeSpawner.spawn(l, kind, p.x, y, p.z, sp.getYRot() + 180 + 35);
                    if (b == null) return 0;
                    b.setNoAi(true);
                    c.getSource().sendSuccess(() -> Component.literal("Spawned " + kind.id()), false);
                    return 1;
                }))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> beastNpc() {
        return Commands.literal("beastnpc").then(Commands.argument("fruit", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(DevilFruits.all().stream().filter(f -> f.type.isZoan()).map(f -> f.id), b))
                .then(Commands.argument("offset", IntegerArgumentType.integer(-40, 40)).executes(c -> beastNpc(c, 14))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(2, 60)).executes(c -> beastNpc(c, IntegerArgumentType.getInteger(c, "distance"))))));
    }

    private static int beastNpc(CommandContext<CommandSourceStack> c, int dist) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String fruit = StringArgumentType.getString(c, "fruit");
        int off = IntegerArgumentType.getInteger(c, "offset");
        ServerLevel l = sp.serverLevel();
        HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
        if (n == null) return 0;
        double yaw = Math.toRadians(sp.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 p = sp.position().add(fwd.scale(dist)).add(side.scale(off));
        int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z));
        float face = sp.getYRot() + 180 + (off < 0 ? 35 : -35); // three-quarter view toward the viewer
        n.moveTo(p.x, y, p.z, face, 0);
        n.configure("villager", 1, "", BlockPos.containing(p), "", l.random);
        n.forceFruitForTest(fruit);
        n.setNoAi(true);
        n.setYHeadRot(face);
        n.yBodyRot = face;
        l.addFreshEntity(n);
        Zoan.setForm(n, "full");
        c.getSource().sendSuccess(() -> Component.literal("Beast NPC " + fruit + " -> " + BeastForms.modelFor(fruit)), false);
        return 1;
    }

    private static int seaKing(CommandContext<CommandSourceStack> c, boolean hold, int dist) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel l = sp.serverLevel();
        String v = StringArgumentType.getString(c, "variant");
        int variant = v.equals("random") ? l.random.nextInt(SeaKing.VARIANTS.length) : Arrays.asList(SeaKing.VARIANTS).indexOf(v);
        if (variant < 0) {
            c.getSource().sendFailure(Component.literal("Unknown variant"));
            return 0;
        }
        int tier = IntegerArgumentType.getInteger(c, "tier");
        float length = SeaKingSpawner.pickLength(tier, l.random);
        float s = length / SeaKing.BASE_LENGTH;
        double d = dist > 0 ? dist : 16 + length * 0.35;
        double yaw = Math.toRadians(sp.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 head = sp.position().add(fwd.scale(d)).add(new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(-length * 0.4));
        float bodyYaw = sp.getYRot() + 90; // broadside: the body trails off to the side
        double headY = hold ? WorldPlan.SEA_LEVEL + 0.6 * s : WorldPlan.SEA_LEVEL - 3 - s;
        SeaKing k = SeaKingSpawner.spawn(l, head.x, head.z, bodyYaw, variant, tier, length, headY);
        if (k == null) return 0;
        k.anchoredForTest = hold;
        c.getSource().sendSuccess(() -> Component.literal(String.format("Sea King %s tier %d, %.0f blocks long, %.0f hp", k.variantName(), k.tier(), k.length(), k.getHealth())), false);
        return 1;
    }

    private CreatureTestCommands() {}
}
