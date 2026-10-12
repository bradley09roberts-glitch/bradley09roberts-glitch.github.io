package com.grandline.combat;

import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.entity.AbilityProjectile;
import com.grandline.fruit.FruitRules;
import com.grandline.fruit.Zoan;
import com.grandline.fruit.impl.FruitKit;
import com.grandline.haki.Haki;
import com.grandline.haki.HakiAbilities;
import com.grandline.item.GLWeaponItem;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Server-authoritative combat: input validation, combos, techniques, ranged fire, dodges and guard. */
public final class CombatManager {

    // ------------------------------------------------------------------ player input

    public static void handle(ServerPlayer sp, GLNetwork.CombatInput in) {
        CharacterData d = Characters.get(sp);
        if (!d.created || d.isKnockedDown() || sp.isSpectator()) return;
        CombatState s = CombatState.of(sp);
        long now = sp.level().getGameTime();
        switch (in.action()) {
            case CombatInput.TOGGLE_COMBAT -> {
                s.combatMode = in.arg0() != 0;
                if (!s.combatMode) s.blocking = false;
                GLNetwork.anim(sp, s.combatMode ? "stance_enter" : "stance_exit");
            }
            case CombatInput.LIGHT -> attack(sp, false, 0);
            case CombatInput.HEAVY -> attack(sp, true, in.arg0());
            case CombatInput.ABILITY -> {
                String id = d.loadoutSlot(in.arg0(), in.arg1());
                Ability a = id == null ? null : Abilities.get(id);
                if (a == null) return;
                Entity t = in.x() >= 0 ? sp.level().getEntity((int) in.x()) : null;
                useAbility(sp, a, t instanceof LivingEntity le && le.distanceTo(sp) < 64 ? le : null);
            }
            case CombatInput.DODGE -> dodge(sp, in.x(), in.z());
            case CombatInput.BLOCK -> {
                boolean on = in.arg0() != 0;
                if (on && !s.blocking) s.blockStart = now;
                s.blocking = on && !s.staggered(now);
                GLNetwork.anim(sp, s.blocking ? "guard" : "guard_end");
            }
            case CombatInput.GLIDE -> {
                s.gliding = in.arg0() != 0 && canGlide(d);
            }
            case CombatInput.LOCK -> s.lockTarget = (int) in.x();
            case CombatInput.TRANSFORM -> {
                if (d.fruit != null && com.grandline.fruit.DevilFruits.get(d.fruit) != null && com.grandline.fruit.DevilFruits.get(d.fruit).type.isZoan()) Zoan.cycle(sp);
            }
            case CombatInput.HAKI_TOGGLE -> Haki.toggle(sp, in.arg0());
            default -> {}
        }
    }

    private static boolean canGlide(CharacterData d) {
        return switch (d.race) {
            case SKY_PEOPLE, STORMWING, LUNARIAN -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ melee

    @Nullable
    public static WeaponClass weaponOf(ItemStack stack) {
        if (stack.getItem() instanceof GLWeaponItem w) return w.weaponClass;
        if (stack.is(net.minecraft.tags.ItemTags.SWORDS)) return WeaponClass.CUTLASS;
        if (stack.is(net.minecraft.tags.ItemTags.AXES)) return WeaponClass.HAMMER;
        if (stack.is(Items.TRIDENT)) return WeaponClass.TRIDENT_POLE;
        if (stack.is(Items.MACE)) return WeaponClass.HAMMER;
        if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return WeaponClass.BOW;
        return null;
    }

    @Nullable
    public static FightingStyle styleOf(LivingEntity e) {
        if (e instanceof Player p) return FightingStyles.get(Characters.get(p).activeStyle);
        if (e instanceof GLNpc n) return FightingStyles.get(n.styleId());
        return FightingStyles.get("brawler");
    }

    /** Resolve the strike for this attack: style moveset when the style fits the held weapon, else the weapon's. */
    public static Strike strikeFor(LivingEntity user, boolean heavy, int step) {
        WeaponClass wc = weaponOf(user.getMainHandItem());
        FightingStyle st = styleOf(user);
        List<Strike> chain;
        Strike hv;
        if (st != null && st.worksWith(wc)) {
            chain = st.light;
            hv = st.heavy;
        } else if (wc != null) {
            chain = wc.light;
            hv = wc.heavy;
        } else {
            FightingStyle b = FightingStyles.get("brawler");
            chain = b.light;
            hv = b.heavy;
        }
        return heavy ? hv : chain.get(Math.floorMod(step, chain.size()));
    }

    public static boolean attack(LivingEntity user, boolean heavy, int charge) {
        if (!(user.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        CombatState s = CombatState.of(user);
        if (s.staggered(now) || now < s.nextAttackAllowed || user.hasEffect(ModEffects.KNOCKED_OUT)) return false;
        WeaponClass wc = weaponOf(user.getMainHandItem());
        if (!heavy && wc != null && wc.ranged) {
            return fireRanged(user, user.getMainHandItem(), false);
        }
        if (now - s.lastAttack > 22) s.comboStep = 0;
        Strike st = strikeFor(user, heavy, s.comboStep);
        float chargeMult = heavy ? 1f + Math.min(10, charge) * 0.05f : 1f;
        if (!Hits.spendStamina(user, st.stamina() * (heavy ? 1.5f : 1f))) {
            if (user instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.combat.no_stamina").withStyle(ChatFormatting.RED), true);
            return false;
        }
        s.lastAttack = now;
        s.comboStep = heavy ? 0 : s.comboStep + 1;
        s.blocking = false;
        float speed = attackSpeedScale(user);
        int windup = Math.max(1, Math.round(st.windup() / speed));
        s.nextAttackAllowed = now + windup + Math.max(2, Math.round(st.recovery() / speed));
        GLNetwork.anim(user, st.anim(), speed);
        if (st.lunge() > 0) FruitKit.impulse(user, user.getLookAngle().multiply(1, 0, 1).normalize().scale(st.lunge() * 0.6), false);
        String mastery = masteryKey(user, wc);
        int masteryLvl = user instanceof Player p ? Characters.get(p).mastery(mastery) : (user instanceof GLNpc n ? n.tier() * 8 : 0);
        s.schedule(now + windup, () -> {
            if (!user.isAlive()) return;
            boolean imbued = Haki.armamentActive(user) || s.hasBuff("infusion");
            float base = Hits.power(user) * st.damage() * chargeMult * (1 + masteryLvl * 0.006f);
            if (s.hasBuff("diable_jambe")) base *= 1.25f;
            if (s.hasBuff("infusion")) base *= 1.3f;
            double reach = st.range() * Math.max(0.6, user.getScale()) + (user instanceof Player p ? Characters.get(p).race.reachBonus * 0.5 : 0);
            Element el = s.hasBuff("diable_jambe") ? Element.FIRE : (st.element() == Element.PHYSICAL && wc != null ? wc.element : st.element());
            Hits.Info info = new Hits.Info(el, st.knockback() * (heavy ? 1.4f : 1f), st.poise() * (heavy ? 1.5f : 1f), 0, imbued, isSeastone(user.getMainHandItem()), false, "hit");
            int hits = 0;
            for (LivingEntity t : Hits.cone(user, reach, st.arc())) {
                Hits.Result r = Hits.hit(user, t, base, info);
                if (r == Hits.Result.HIT || r == Hits.Result.BLOCKED) hits++;
                if (st.arc() < 60 && hits > 0) break; // thrusts hit one target
            }
            if (hits > 0) {
                Hits.train(user, mastery, 2 + (heavy ? 2 : 0));
                Hits.train(user, "combat/hits", 1);
                if (imbued) Hits.train(user, Haki.ARM, 1);
                if (user.getMainHandItem().isDamageableItem()) user.getMainHandItem().hurtAndBreak(1, user, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
            level.playSound(null, user.blockPosition(), hits > 0 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 0.9f + user.getRandom().nextFloat() * 0.3f);
        });
        return true;
    }

    private static String masteryKey(LivingEntity user, @Nullable WeaponClass wc) {
        FightingStyle st = styleOf(user);
        if (st != null && st.worksWith(wc)) return "style/" + st.id;
        if (wc != null) return "weapon/" + wc.id();
        return "style/brawler";
    }

    private static boolean isSeastone(ItemStack s) {
        return s.getItem() instanceof GLWeaponItem w && w.weaponClass == WeaponClass.JITTE;
    }

    private static float attackSpeedScale(LivingEntity e) {
        var inst = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        double v = inst == null ? 4 : inst.getValue();
        return (float) Math.max(0.6, Math.min(1.8, v / 1.6));
    }

    // ------------------------------------------------------------------ ranged

    public static boolean fireRanged(LivingEntity user, ItemStack stack, boolean aimed) {
        WeaponClass wc = weaponOf(stack);
        if (wc == null || !wc.ranged || !(user.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        CombatState s = CombatState.of(user);
        if (now < s.nextAttackAllowed || s.staggered(now)) return false;
        String kind;
        double speed;
        float mult;
        float explosion = 0;
        double gravity = 0.01;
        ItemStack ammo = ItemStack.EMPTY;
        switch (wc) {
            case FLINTLOCK -> { kind = "bullet"; speed = 3.6; mult = 2.2f; ammo = findAmmo(user, ModItems.BULLET.get()); }
            case RIFLE -> { kind = "bullet"; speed = 4.6; mult = 3.0f; ammo = findAmmo(user, ModItems.BULLET.get()); }
            case BAZOOKA, HAND_CANNON -> { kind = "cannonball"; speed = 2.0; mult = 3.5f; explosion = 2.8f; gravity = 0.025; ammo = findAmmo(user, ModItems.CANNONBALL.get()); }
            case SLINGSHOT -> { kind = "pellet"; speed = 2.8; mult = 1.6f; ammo = findAmmo(user, ModItems.PELLET.get()); }
            case BOW -> { kind = "arrow"; speed = 3.0; mult = 2.2f; ammo = findAmmo(user, Items.ARROW); }
            case THROWING_KNIVES -> { kind = "knife"; speed = 2.4; mult = 1.6f; ammo = stack; }
            case WEATHER_STAFF -> { kind = "weather_ball"; speed = 1.6; mult = 1.4f; }
            case DIAL -> { kind = "impact_wave"; speed = 1.6; mult = 1.6f; }
            default -> { return false; }
        }
        boolean infinite = user instanceof Player p && p.isCreative() || user instanceof GLNpc;
        if (wc != WeaponClass.WEATHER_STAFF && wc != WeaponClass.DIAL && !infinite) {
            if (ammo.isEmpty()) {
                if (user instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.combat.no_ammo"), true);
                return false;
            }
            ammo.shrink(1);
        }
        float dmg = Math.max(2f, Hits.power(user)) * mult * (aimed ? 1.3f : 1f);
        Vec3 dir = user.getLookAngle();
        if (user instanceof GLNpc n && n.getTarget() != null) dir = n.getTarget().getEyePosition().subtract(user.getEyePosition()).normalize();
        Vec3 from = user.getEyePosition().add(dir.scale(0.6)).add(0, -0.1, 0);
        AbilityProjectile p = AbilityProjectile.shoot(user, kind, kind.equals("cannonball") ? 0.45f : 0.16f, from, dir, speed, dmg,
                Hits.Info.of(wc.element, explosion > 0 ? 1.5f : 0.4f).withPoise(20));
        p.gravity = gravity;
        p.explosion = explosion;
        p.breaksBlocks = explosion > 0;
        if (wc == WeaponClass.RIFLE) p.pierce = true;
        s.nextAttackAllowed = now + (stack.getItem() instanceof GLWeaponItem w ? w.cooldown() : 16);
        GLNetwork.anim(user, wc.twoHanded ? "fire_two_hand" : "fire_one_hand");
        level.playSound(null, user.blockPosition(), explosion > 0 ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, explosion > 0 ? 0.6f : 1f, explosion > 0 ? 1.4f : 1.6f);
        GLNetwork.fx(user, "muzzle", from.x, from.y, from.z, (float) dir.x, (float) dir.y, (float) dir.z);
        Hits.train(user, "weapon/" + wc.id(), 1);
        return true;
    }

    private static ItemStack findAmmo(LivingEntity user, net.minecraft.world.item.Item ammo) {
        if (!(user instanceof Player p)) return ItemStack.EMPTY;
        if (p.getOffhandItem().is(ammo)) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) if (s.is(ammo)) return s;
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ techniques

    public static boolean canUse(LivingEntity user, Ability a) {
        if (user instanceof Player p) {
            CharacterData d = Characters.get(p);
            if (a.source() == Ability.Source.FRUIT && !a.owner().equals(d.fruit)) return false;
            if (a.source() == Ability.Source.STYLE && !d.knownStyles.contains(a.owner())) return false;
            if (a.awakened() && !d.fruitAwakened) return false;
            if (!p.isCreative() && d.mastery(a.masteryKey()) < a.unlockMastery()) return false;
            if (a.source() == Ability.Source.HAKI && !HakiAbilities.canUse(user, a)) return false;
        }
        if (a.source() == Ability.Source.WEAPON) {
            WeaponClass wc = weaponOf(user.getMainHandItem());
            if (wc == null || !wc.id().equals(a.owner())) return false;
        }
        if (a.source() == Ability.Source.STYLE) {
            FightingStyle st = FightingStyles.get(a.owner());
            if (st != null && !st.unarmed() && !st.worksWith(weaponOf(user.getMainHandItem()))) return false;
        }
        return true;
    }

    public static boolean useAbility(LivingEntity user, Ability a, @Nullable LivingEntity target) {
        if (!(user.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        CombatState s = CombatState.of(user);
        if (s.staggered(now) || user.hasEffect(ModEffects.KNOCKED_OUT)) return false;
        if (!canUse(user, a)) {
            if (user instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.ability.locked", a.name(), a.unlockMastery()).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (a.source() == Ability.Source.FRUIT && FruitRules.weakened(user)) {
            if (user instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.fruit.weakened").withStyle(ChatFormatting.AQUA), true);
            return false;
        }
        if (s.onCooldown(a.id(), now)) return false;
        if (a.haki() > 0 && !Haki.spendGauge(user, a.haki())) return false;
        if (!Hits.spendStamina(user, a.stamina())) {
            if (user instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("grandline.combat.no_stamina").withStyle(ChatFormatting.RED), true);
            return false;
        }
        CharacterData d = user instanceof Player p ? Characters.get(p) : null;
        int mastery = d != null ? d.mastery(a.masteryKey()) : (user instanceof GLNpc n ? Math.min(100, n.tier() * 10) : 0);
        boolean awakened = d != null ? d.fruitAwakened : (user instanceof GLNpc n && n.tier() >= 9);
        if (target == null && user instanceof GLNpc n) target = n.getTarget();
        AbilityContext ctx = new AbilityContext(level, user, d, target, mastery, awakened, Hits.abilityPower(user));
        if (!a.anim().isEmpty()) GLNetwork.anim(user, a.anim());
        boolean ok = a.exec().run(user, ctx);
        if (!ok) return false;
        s.startCooldown(a.id(), now, Math.max(4, Math.round(a.cooldown() * (d != null ? 1f - Math.min(0.3f, d.stat(com.grandline.player.Stat.TECHNIQUE) * 0.006f) : 1f))));
        s.nextAttackAllowed = Math.max(s.nextAttackAllowed, now + 6);
        Hits.train(user, a.masteryKey(), 3);
        if (user instanceof ServerPlayer sp) CharacterManager.markDirty(sp);
        return true;
    }

    // ------------------------------------------------------------------ dodge

    public static boolean dodge(LivingEntity user, float lx, float lz) {
        if (!(user.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        CombatState s = CombatState.of(user);
        if (s.staggered(now) || s.onCooldown("dodge", now) || user.hasEffect(ModEffects.KNOCKED_OUT)) return false;
        if (!Hits.spendStamina(user, 16)) return false;
        s.startCooldown("dodge", now, 12);
        s.iframesUntil = now + (s.hasBuff("kami_e") ? 14 : 8);
        s.blocking = false;
        Vec3 look = user.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        Vec3 dir = look.scale(lz).add(right.scale(lx));
        if (dir.lengthSqr() < 0.01) dir = look.scale(-1);
        dir = dir.normalize();
        double power = 1.05 * (user instanceof Player p ? 1 + Characters.get(p).stat(com.grandline.player.Stat.AGILITY) * 0.01 : 1);
        FruitKit.impulse(user, new Vec3(dir.x * power, 0.18, dir.z * power), true);
        String anim = Math.abs(lx) > Math.abs(lz) ? (lx > 0 ? "dodge_right" : "dodge_left") : (lz > 0 ? "dodge_forward" : "dodge_back");
        GLNetwork.anim(user, anim);
        Hits.train(user, "combat/dodge", 1);
        return true;
    }

    private CombatManager() {}
}
