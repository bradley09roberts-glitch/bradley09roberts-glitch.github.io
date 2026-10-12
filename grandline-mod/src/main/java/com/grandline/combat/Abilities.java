package com.grandline.combat;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Global ability registry populated by static initialisers of weapons, styles, Haki and fruits. */
public final class Abilities {
    private static final Map<String, Ability> BY_ID = new LinkedHashMap<>();
    private static final Map<String, List<Ability>> BY_OWNER = new HashMap<>();

    static void register(Ability a) {
        if (BY_ID.containsKey(a.id())) throw new IllegalStateException("Duplicate ability " + a.id());
        BY_ID.put(a.id(), a);
        BY_OWNER.computeIfAbsent(a.source().name() + ":" + a.owner(), k -> new ArrayList<>()).add(a);
    }

    @Nullable
    public static Ability get(String id) {
        return BY_ID.get(id);
    }

    public static List<Ability> of(Ability.Source source, String owner) {
        return BY_OWNER.getOrDefault(source.name() + ":" + owner, List.of());
    }

    public static Collection<Ability> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Force class-loading of every ability source so registries are complete. */
    public static void bootstrap() {
        com.grandline.combat.style.FightingStyles.bootstrap();
        com.grandline.combat.weapon.WeaponArts.bootstrap();
        com.grandline.haki.HakiAbilities.bootstrap();
        com.grandline.fruit.DevilFruits.bootstrap();
    }

    private Abilities() {}
}
