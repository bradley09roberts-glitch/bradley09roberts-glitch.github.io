package com.terracraft.npc;

import com.terracraft.entity.projectile.ProjectileKind;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Definition of a town NPC: names, when it moves in, how it defends itself and which services its chat
 * window offers. Dialogue lines are {@code npc.terracraft.<id>.dialogue.<n>} lang entries; shops are
 * datapack JSON ({@code data/<ns>/terracraft/shops/<id>.json}).
 *
 * @param arrival  checked during the day while the NPC is absent (the Guide's is always true)
 * @param needsHouse  whether the NPC only arrives when a free valid house exists
 * @param attack   projectile the NPC throws/shoots at nearby enemies (null = flees instead)
 */
public record TownNpcType(
    String id,
    Supplier<? extends EntityType<? extends TownNpc>> entity,
    List<String> names,
    Predicate<MinecraftServer> arrival,
    boolean needsHouse,
    @Nullable Supplier<ProjectileKind> attack,
    float attackDamage,
    int dialogueLines,
    List<Service> services
) {
    public enum Service { SHOP, HEAL, HELP, CURSE, REFORGE }

    public String roleKey() {
        return "npc.terracraft." + id;
    }
}
