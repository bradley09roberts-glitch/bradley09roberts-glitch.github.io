package com.grandline.quest.story;

import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.story.JobBoard.StartGift;
import com.grandline.ship.ShipEntity;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Sea-job marks on the sea chart (Navigation.chartData), for the player who holds the jobs only: a named pirate
 * ship's last-seen point ("sink"; plus where she is now once she has been sighted and is afloat), a convoy's port of
 * destination and where her merchantman lies, and an escort's destination dock. Each mark: kind, x, z, the ship's or
 * merchant's name and the island (id, plus its generated name; canon islands are named by the client's translation).
 */
public final class SeaJobMarks {
    public static ListTag chartMarks(ServerPlayer sp) {
        ListTag out = new ListTag();
        CharacterData d = Characters.get(sp);
        if (d.questProgress.isEmpty()) return out;
        WorldPlan plan = WorldPlan.of(sp.server);
        ServerLevel ow = sp.server.overworld();
        for (String q : d.questProgress.keySet()) {
            StartGift g = JobBoard.gift(q);
            if (g == null) continue;
            switch (g.kind()) {
                case SeaJobs.SINK -> {
                    out.add(mark("sink", g.x(), g.z(), g.name(), null));
                    ShipEntity s = SeaBattleJobs.named(ow, QuestRegistry.baseId(q));
                    if (s != null) out.add(mark("sighted", s.getBlockX(), s.getBlockZ(), g.name(), null));
                }
                case SeaJobs.CONVOY -> {
                    Island dest = plan == null ? null : plan.byId(g.target());
                    out.add(mark("convoy_port", g.x(), g.z(), g.name(), dest));
                    SeaJobs.Convoy c = SeaJobs.data(sp.server).convoys.get(SeaJobs.key(d.characterId, q));
                    if (c != null && c.stage > 0) {
                        ShipEntity s = SeaBattleJobs.convoyShip(ow, c);
                        out.add(mark("convoy_ship", s != null ? s.getBlockX() : (int) c.x, s != null ? s.getBlockZ() : (int) c.z, g.name(), dest));
                    }
                }
                case SeaJobs.ESCORT -> {
                    Island dest = plan == null ? null : plan.byId(g.target());
                    out.add(mark("escort_port", g.x(), g.z(), g.name(), dest));
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static CompoundTag mark(String kind, int x, int z, String name, @Nullable Island isl) {
        CompoundTag e = new CompoundTag();
        e.putString("kind", kind);
        e.putInt("x", x);
        e.putInt("z", z);
        e.putString("name", name);
        e.putString("island", isl == null ? "" : isl.id);
        e.putString("islandName", isl == null || isl.name == null ? "" : isl.name);
        return e;
    }

    private SeaJobMarks() {}
}
