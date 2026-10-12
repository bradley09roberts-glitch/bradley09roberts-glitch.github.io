package com.grandline.fruit;

import com.grandline.combat.CombatState;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.GrandLine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Zoan transformation state (none → hybrid → full → back). Stats change per form; model swaps on the client. */
public final class Zoan {
    private static final ResourceLocation MOD = GrandLine.id("zoan_form");

    public static String form(LivingEntity e) {
        if (e instanceof Player p) return Characters.get(p).zoanForm;
        if (e instanceof com.grandline.npc.GLNpc n) return n.zoanForm();
        return "";
    }

    /** Cycle forms. Returns the new form. */
    public static String cycle(ServerPlayer sp) {
        var d = Characters.get(sp);
        DevilFruit f = d.fruit == null ? null : DevilFruits.get(d.fruit);
        if (f == null || !f.type.isZoan() || FruitRules.weakened(sp)) return d.zoanForm;
        int mastery = d.mastery("fruit/" + f.id);
        String next = switch (d.zoanForm) {
            case "" -> "hybrid";
            case "hybrid" -> mastery >= 10 ? "full" : "";
            case "full" -> d.fruitAwakened ? "awakened" : "";
            default -> "";
        };
        setForm(sp, next);
        return next;
    }

    public static void setForm(LivingEntity e, String form) {
        if (e instanceof ServerPlayer sp) {
            Characters.get(sp).zoanForm = form;
            CharacterManager.markVisualsDirty(sp);
        } else if (e instanceof com.grandline.npc.GLNpc n) {
            n.setZoanForm(form);
        }
        applyStats(e, form);
        com.grandline.net.GLNetwork.anim(e, form.isEmpty() ? "transform_revert" : "transform");
        com.grandline.net.GLNetwork.fx(e, "transform", e.getX(), e.getY() + 1, e.getZ(), 0, 0, 0);
    }

    public static void applyStats(LivingEntity e, String form) {
        double scale = switch (form) { case "hybrid" -> 0.25; case "full" -> 0.6; case "awakened" -> 0.9; default -> 0; };
        double hp = switch (form) { case "hybrid" -> 6; case "full" -> 14; case "awakened" -> 24; default -> 0; };
        double dmg = switch (form) { case "hybrid" -> 0.25; case "full" -> 0.4; case "awakened" -> 0.7; default -> 0; };
        set(e.getAttribute(Attributes.SCALE), scale, AttributeModifier.Operation.ADD_VALUE);
        set(e.getAttribute(Attributes.MAX_HEALTH), hp, AttributeModifier.Operation.ADD_VALUE);
        set(e.getAttribute(Attributes.ATTACK_DAMAGE), dmg, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(e.getAttribute(Attributes.ARMOR), hp / 3, AttributeModifier.Operation.ADD_VALUE);
        set(e.getAttribute(Attributes.KNOCKBACK_RESISTANCE), scale * 0.5, AttributeModifier.Operation.ADD_VALUE);
        if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
        e.refreshDimensions();
    }

    private static void set(AttributeInstance inst, double amount, AttributeModifier.Operation op) {
        if (inst == null) return;
        if (amount == 0) inst.removeModifier(MOD);
        else inst.addOrUpdateTransientModifier(new AttributeModifier(MOD, amount, op));
    }

    public static void forceRevert(LivingEntity e) {
        if (!form(e).isEmpty()) setForm(e, "");
        CombatState.of(e).toggles.removeIf(t -> t.startsWith("zoan"));
    }

    private Zoan() {}
}
