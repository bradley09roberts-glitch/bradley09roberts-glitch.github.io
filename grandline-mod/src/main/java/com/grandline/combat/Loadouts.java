package com.grandline.combat;

import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ability slot sets: 0 = martial (style + weapon arts), 1 = Devil Fruit, 2 = Haki, 3 = race.
 * Slots auto-fill with unlocked techniques; players can rearrange them in the abilities screen.
 */
public final class Loadouts {
    public static void resetDefaults(ServerPlayer sp, CharacterData d) {
        for (String[] set : d.loadout) Arrays.fill(set, null);
        refresh(sp, d);
    }

    /** Fill empty slots with newly unlocked techniques and drop ones that no longer apply. */
    public static void refresh(ServerPlayer sp, CharacterData d) {
        List<String> martial = new ArrayList<>();
        FightingStyle st = FightingStyles.get(d.activeStyle);
        if (st != null) for (Ability a : st.abilities) if (sp.isCreative() || d.mastery(a.masteryKey()) >= a.unlockMastery()) martial.add(a.id());
        var wc = CombatManager.weaponOf(sp.getMainHandItem());
        if (wc != null) for (Ability a : Abilities.of(Ability.Source.WEAPON, wc.id())) if (sp.isCreative() || d.mastery(a.masteryKey()) >= a.unlockMastery()) martial.add(a.id());
        fill(d.loadout[0], martial, id -> {
            Ability a = Abilities.get(id);
            return a != null && (a.source() == Ability.Source.STYLE ? a.owner().equals(d.activeStyle) : a.source() == Ability.Source.WEAPON);
        });

        List<String> fruit = new ArrayList<>();
        DevilFruit f = d.fruit == null ? null : DevilFruits.get(d.fruit);
        if (f != null) for (Ability a : f.abilities()) if ((sp.isCreative() || d.mastery(a.masteryKey()) >= a.unlockMastery()) && (!a.awakened() || d.fruitAwakened)) fruit.add(a.id());
        fill(d.loadout[1], fruit, id -> {
            Ability a = Abilities.get(id);
            return a != null && f != null && a.owner().equals(f.id);
        });

        List<String> haki = new ArrayList<>();
        for (Ability a : Abilities.all()) {
            if (a.source() != Ability.Source.HAKI) continue;
            if (com.grandline.haki.HakiAbilities.canUse(sp, a) && (sp.isCreative() || d.mastery(a.masteryKey()) >= a.unlockMastery())) haki.add(a.id());
        }
        fill(d.loadout[2], haki, id -> Abilities.get(id) != null && Abilities.get(id).source() == Ability.Source.HAKI);

        List<String> race = new ArrayList<>();
        for (Ability a : Abilities.of(Ability.Source.RACE, d.race.id())) if (sp.isCreative() || d.mastery(a.masteryKey()) >= a.unlockMastery()) race.add(a.id());
        fill(d.loadout[3], race, id -> {
            Ability a = Abilities.get(id);
            return a != null && a.source() == Ability.Source.RACE && a.owner().equals(d.race.id());
        });
        CharacterManager.markDirty(sp);
    }

    private static void fill(String[] slots, List<String> available, java.util.function.Predicate<String> stillValid) {
        for (int i = 0; i < slots.length; i++) if (slots[i] != null && (!stillValid.test(slots[i]) || !available.contains(slots[i]))) slots[i] = null;
        for (String id : available) {
            if (Arrays.asList(slots).contains(id)) continue;
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null) {
                    slots[i] = id;
                    break;
                }
            }
        }
    }

    private Loadouts() {}
}
